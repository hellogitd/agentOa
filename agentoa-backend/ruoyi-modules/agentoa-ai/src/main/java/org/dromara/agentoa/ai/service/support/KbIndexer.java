package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.mapper.OaAiKbChunkMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbSourceMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 知识域索引管线（docs/21 AI-M3-03/04）：清洗 → 分块 → 向量化 → 存储。
 * <p>
 * 索引在独立线程池执行，不占用 Web 线程（docs/21 §8）；失败置 failed 可重试。
 */
@Component
public class KbIndexer {

    /** 每批向量化文本数 */
    public static final int EMBED_BATCH = 16;

    private final OaAiKbMapper kbMapper;
    private final OaAiKbSourceMapper sourceMapper;
    private final OaAiKbChunkMapper chunkMapper;
    private final KbDocumentAccess documentAccess;
    private final KbFileStore fileStore;
    private final LlmGateway gateway;
    private final KbVectorCache vectorCache;
    private final ThreadPoolExecutor executor;
    private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(0);

    public KbIndexer(OaAiKbMapper kbMapper, OaAiKbSourceMapper sourceMapper, OaAiKbChunkMapper chunkMapper,
                     KbDocumentAccess documentAccess, KbFileStore fileStore, LlmGateway gateway,
                     KbVectorCache vectorCache) {
        this.kbMapper = kbMapper;
        this.sourceMapper = sourceMapper;
        this.chunkMapper = chunkMapper;
        this.documentAccess = documentAccess;
        this.fileStore = fileStore;
        this.gateway = gateway;
        this.vectorCache = vectorCache;
        this.executor = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500), runnable -> {
                Thread thread = new Thread(runnable, "ai-kb-indexer");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 异步索引（提交后立即返回） */
    public void submit(OaAiKbSource source, Long operatorId, String username) {
        pending.incrementAndGet();
        executor.execute(() -> {
            try {
                indexNow(source, operatorId, username);
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    /** 等待队列清空（测试与优雅停机用） */
    public void awaitIdle() {
        try {
            while (pending.get() > 0) {
                Thread.sleep(10L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 同步索引一个数据源。
     *
     * @throws ServiceException embedding 模型未配置等前置错误（同步抛出）
     */
    public void indexNow(OaAiKbSource source, Long operatorId, String username) {
        OaAiKb kb = kbMapper.selectById(source.getKbId());
        if (kb == null) {
            throw new ServiceException("AI_KB_NOT_FOUND 知识域不存在", 404);
        }
        Long embeddingModelId = kb.getEmbeddingModelId();
        if (embeddingModelId == null) {
            throw new ServiceException("EMBEDDING_MODEL_UNAVAILABLE 知识域未配置向量模型", 400);
        }
        source.setIndexStatus(OaAiKbSource.STATUS_INDEXING);
        source.setErrorMsg(null);
        sourceMapper.updateById(source);
        try {
            String text = loadText(source);
            List<AiTextChunker.Chunk> chunks = AiTextChunker.chunk(clean(text));
            List<float[]> vectors = embedAll(chunks, embeddingModelId, source, operatorId, username);

            chunkMapper.delete(new LambdaQueryWrapper<OaAiKbChunk>().eq(OaAiKbChunk::getSourceId, source.getId()));
            for (int i = 0; i < chunks.size(); i++) {
                AiTextChunker.Chunk chunk = chunks.get(i);
                OaAiKbChunk row = new OaAiKbChunk();
                row.setKbId(source.getKbId());
                row.setSourceId(source.getId());
                row.setSeq(chunk.seq());
                row.setHeading(chunk.heading());
                row.setContent(chunk.content());
                row.setTokenCount(chunk.tokenCount());
                row.setEmbedding(KbVectors.toBytes(vectors.get(i)));
                row.setStoreType(OaAiKbChunk.STORE_BLOB);
                row.setCreateBy(operatorId);
                chunkMapper.insert(row);
            }
            source.setChunkCount(chunks.size());
            source.setIndexStatus(OaAiKbSource.STATUS_READY);
            source.setIndexedAt(new Date());
            source.setErrorMsg(null);
            persistStatus(source);
            vectorCache.invalidate(source.getKbId());
        } catch (ServiceException e) {
            markFailed(source, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            markFailed(source, e.getMessage());
            throw e;
        }
    }

    /** 删除数据源及其分块（docs/21 AI-M3-03 删除触发失效） */
    public void deleteSource(OaAiKbSource source) {
        chunkMapper.delete(new LambdaQueryWrapper<OaAiKbChunk>().eq(OaAiKbChunk::getSourceId, source.getId()));
        sourceMapper.deleteById(source.getId());
        if (source.getKbId() != null) {
            vectorCache.invalidate(source.getKbId());
        }
        if (OaAiKbSource.TYPE_FILE.equals(source.getSourceType()) && source.getFileId() != null) {
            fileStore.delete(source.getFileId());
        }
    }

    /** 删除知识域全部分块与数据源 */
    public void deleteKb(Long kbId) {
        chunkMapper.delete(new LambdaQueryWrapper<OaAiKbChunk>().eq(OaAiKbChunk::getKbId, kbId));
        List<OaAiKbSource> sources = sourceMapper.selectList(new LambdaQueryWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getKbId, kbId));
        for (OaAiKbSource source : sources) {
            if (OaAiKbSource.TYPE_FILE.equals(source.getSourceType()) && source.getFileId() != null) {
                fileStore.delete(source.getFileId());
            }
        }
        sourceMapper.delete(new LambdaQueryWrapper<OaAiKbSource>().eq(OaAiKbSource::getKbId, kbId));
        vectorCache.invalidate(kbId);
    }

    // ---------------------------------------------------------------- internals

    private String loadText(OaAiKbSource source) {
        if (OaAiKbSource.TYPE_DOCUMENT.equals(source.getSourceType())) {
            if (source.getDocId() == null) {
                throw new ServiceException("AI_KB_SOURCE_INVALID 文档来源缺少 docId", 400);
            }
            KbDocumentAccess.DocumentContent content = documentAccess.load(source.getDocId());
            if (content == null) {
                throw new ServiceException("AI_KB_SOURCE_MISSING 源文档不存在或已删除", 400);
            }
            return content.text();
        }
        if (source.getFileId() == null) {
            throw new ServiceException("AI_KB_SOURCE_INVALID 文件来源缺少 fileId", 400);
        }
        byte[] bytes = fileStore.read(source.getFileId());
        return AiDocumentParser.parse(source.getTitle(), bytes);
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return AiTextChunker.stripMarkdown(text);
    }

    private List<float[]> embedAll(List<AiTextChunker.Chunk> chunks, Long embeddingModelId,
                                   OaAiKbSource source, Long operatorId, String username) {
        List<float[]> vectors = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += EMBED_BATCH) {
            List<AiTextChunker.Chunk> batch = chunks.subList(start, Math.min(chunks.size(), start + EMBED_BATCH));
            List<String> texts = batch.stream().map(AiTextChunker.Chunk::content).toList();
            List<float[]> embedded = gateway.embed(new LlmGateway.EmbeddingRequest(operatorId, username, "rag",
                "kb-source:" + source.getId(), embeddingModelId, texts)).vectors();
            if (embedded.size() != texts.size()) {
                throw new ServiceException("LLM_UPSTREAM_ERROR 向量化结果数量不一致", 502);
            }
            vectors.addAll(embedded);
        }
        return vectors;
    }

    private void markFailed(OaAiKbSource source, String message) {
        source.setIndexStatus(OaAiKbSource.STATUS_FAILED);
        source.setErrorMsg(message == null ? "索引失败" : truncate(message));
        persistStatus(source);
    }

    /** 显式写列：error_msg 需允许写 null（updateById 默认跳过 null 字段） */
    private void persistStatus(OaAiKbSource source) {
        sourceMapper.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getId, source.getId())
            .set(OaAiKbSource::getIndexStatus, source.getIndexStatus())
            .set(OaAiKbSource::getChunkCount, source.getChunkCount())
            .set(OaAiKbSource::getIndexedAt, source.getIndexedAt())
            .set(OaAiKbSource::getErrorMsg, source.getErrorMsg()));
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
