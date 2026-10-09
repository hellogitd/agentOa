package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.mapper.OaAiKbChunkMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbSourceMapper;
import org.dromara.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索（docs/21 AI-M3-05/07）：向量 top-k + 关键词 LIKE 候选 → 简单融合排序。
 * <p>
 * 权限过滤在服务端查询条件内完成：仅索引就绪且提问人可见的数据源进入候选，
 * 文档型来源按知识库 ACL 可见集过滤，引用片段不越权泄露原文。
 */
@Component
@RequiredArgsConstructor
public class KbRetriever {

    /** 默认向量 top-k */
    public static final int DEFAULT_TOP_K = 5;
    /** 关键词候选上限 */
    public static final int KEYWORD_CANDIDATES = 20;
    /** 融合权重：向量相似度 */
    private static final double VECTOR_WEIGHT = 0.7d;
    /** 融合权重：关键词命中 */
    private static final double KEYWORD_WEIGHT = 0.3d;

    /** 检索遥测（docs/21 §9.2 向量库切换阈值：扫描规模与检索耗时） */
    private static final Logger log = LoggerFactory.getLogger(KbRetriever.class);

    private final OaAiKbChunkMapper chunkMapper;
    private final OaAiKbSourceMapper sourceMapper;
    private final KbVectorCache vectorCache;
    private final KbDocumentAccess documentAccess;
    private final LlmGateway gateway;

    /** 知识域集合使用的向量模型分组（docs/21 AI-M3-04）：向量模型 -> kbIds，异构模型各查各的 */
    public static Map<Long, List<Long>> groupKbsByEmbeddingModel(List<OaAiKb> kbs) {
        Map<Long, List<Long>> groups = new LinkedHashMap<>();
        if (kbs != null) {
            for (OaAiKb kb : kbs) {
                if (kb.getEmbeddingModelId() != null) {
                    groups.computeIfAbsent(kb.getEmbeddingModelId(), key -> new ArrayList<>()).add(kb.getId());
                }
            }
        }
        if (groups.isEmpty()) {
            throw new ServiceException("EMBEDDING_MODEL_UNAVAILABLE 知识域未配置向量模型", 400);
        }
        return groups;
    }

    public List<AiKbSearchHitVo> search(Long userId, String username, List<OaAiKb> kbs,
                                        String query, int topK) {
        long startedNanos = System.nanoTime();
        int limit = topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 20);
        List<Long> kbIds = kbs.stream().map(OaAiKb::getId).toList();
        if (kbIds.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        // 查询向量化：按知识域绑定的向量模型分组各查各的（先于可见性短路，见 docs/21 §9.2 契约修复）
        Map<Long, List<Long>> modelGroups = groupKbsByEmbeddingModel(kbs);
        Map<Long, float[]> queryVectors = new HashMap<>();
        for (Map.Entry<Long, List<Long>> group : modelGroups.entrySet()) {
            float[] vector = gateway.embed(new LlmGateway.EmbeddingRequest(userId, username, "rag", null,
                group.getKey(), List.of(query.strip()))).vectors().stream().findFirst().orElse(null);
            if (vector != null) {
                queryVectors.put(group.getKey(), vector);
            }
        }
        Set<Long> visibleSources = visibleSourceIds(userId, kbIds);
        if (visibleSources.isEmpty()) {
            return List.of();
        }

        Map<Long, Candidate> candidates = new HashMap<>();
        int scannedChunks = 0;
        if (!queryVectors.isEmpty()) {
            for (OaAiKb kb : kbs) {
                float[] queryVector = kb.getEmbeddingModelId() == null ? null : queryVectors.get(kb.getEmbeddingModelId());
                if (queryVector == null) {
                    continue;
                }
                KbVectorCache.Snapshot snapshot = vectorCache.snapshot(kb.getId(), () -> KbVectorCache.build(
                    chunkMapper.selectList(new LambdaQueryWrapper<OaAiKbChunk>()
                        .select(OaAiKbChunk::getId, OaAiKbChunk::getSourceId, OaAiKbChunk::getEmbedding)
                        .eq(OaAiKbChunk::getKbId, kb.getId())
                        .isNotNull(OaAiKbChunk::getEmbedding))));
                scannedChunks += snapshot.chunkIds().size();
                for (int i = 0; i < snapshot.chunkIds().size(); i++) {
                    if (!visibleSources.contains(snapshot.sourceIds().get(i))) {
                        continue;
                    }
                    float score = KbVectors.cosine(queryVector, snapshot.vectors().get(i));
                    if (score <= 0f) {
                        continue;
                    }
                    Candidate candidate = candidates.computeIfAbsent(snapshot.chunkIds().get(i), Candidate::new);
                    candidate.vectorScore = Math.max(candidate.vectorScore == null ? 0f : candidate.vectorScore, score);
                }
            }
        }

        // 关键词腿（服务端过滤可见数据源）
        List<OaAiKbChunk> keywordRows = chunkMapper.selectList(new LambdaQueryWrapper<OaAiKbChunk>()
            .select(OaAiKbChunk::getId)
            .in(OaAiKbChunk::getKbId, kbIds)
            .in(OaAiKbChunk::getSourceId, visibleSources)
            .like(OaAiKbChunk::getContent, query.strip())
            .orderByAsc(OaAiKbChunk::getId)
            .last("LIMIT " + KEYWORD_CANDIDATES));
        int keywordCount = keywordRows.size();
        for (int i = 0; i < keywordCount; i++) {
            Candidate candidate = candidates.computeIfAbsent(keywordRows.get(i).getId(), Candidate::new);
            candidate.keywordRank = i;
        }

        List<Candidate> ranked = new ArrayList<>(candidates.values());
        for (Candidate candidate : ranked) {
            double vector = candidate.vectorScore == null ? 0d : Math.max(0d, candidate.vectorScore);
            double keyword = candidate.keywordRank == null ? 0d
                : KEYWORD_WEIGHT * (1.0d - (double) candidate.keywordRank / Math.max(1, keywordCount));
            candidate.fused = VECTOR_WEIGHT * vector + keyword;
            candidate.displayScore = candidate.vectorScore == null ? (candidate.keywordRank == null ? 0d : 0.25d)
                : (double) candidate.vectorScore;
        }
        ranked.sort(Comparator.comparingDouble((Candidate c) -> c.fused).reversed()
            .thenComparing(c -> c.chunkId));
        if (ranked.size() > limit) {
            ranked = ranked.subList(0, limit);
        }
        List<AiKbSearchHitVo> hits = materialize(ranked);
        log.info("RAG 检索完成 kbs={} 扫描分块={} 候选={} 命中={} 耗时ms={}", kbIds.size(), scannedChunks,
            candidates.size(), hits.size(), (System.nanoTime() - startedNanos) / 1_000_000);
        return hits;
    }

    // ---------------------------------------------------------------- internals

    private static final class Candidate {
        private final Long chunkId;
        private Float vectorScore;
        private Integer keywordRank;
        private double fused;
        private double displayScore;

        private Candidate(Long chunkId) {
            this.chunkId = chunkId;
        }
    }

    /** 数据源可见集：直传文件按知识域可见；文档来源按提问人知识库 ACL 可见集（服务端过滤） */
    private Set<Long> visibleSourceIds(Long userId, List<Long> kbIds) {
        Set<Long> visibleDocs = documentAccess.visibleDocumentIds(userId);
        Set<Long> result = new LinkedHashSet<>();
        for (OaAiKbSource source : sourceMapper.selectList(new LambdaQueryWrapper<OaAiKbSource>()
            .select(OaAiKbSource::getId, OaAiKbSource::getSourceType, OaAiKbSource::getDocId)
            .in(OaAiKbSource::getKbId, kbIds)
            .eq(OaAiKbSource::getIndexStatus, OaAiKbSource.STATUS_READY))) {
            if (OaAiKbSource.TYPE_FILE.equals(source.getSourceType())) {
                result.add(source.getId());
            } else if (source.getDocId() != null && visibleDocs.contains(source.getDocId())) {
                result.add(source.getId());
            }
        }
        return result;
    }

    private List<AiKbSearchHitVo> materialize(List<Candidate> ranked) {
        List<AiKbSearchHitVo> hits = new ArrayList<>();
        if (ranked.isEmpty()) {
            return hits;
        }
        List<Long> chunkIds = ranked.stream().map(candidate -> candidate.chunkId).toList();
        Map<Long, OaAiKbChunk> chunks = new HashMap<>();
        for (OaAiKbChunk chunk : chunkMapper.selectBatchIds(chunkIds)) {
            chunks.put(chunk.getId(), chunk);
        }
        Set<Long> sourceIds = new LinkedHashSet<>();
        for (OaAiKbChunk chunk : chunks.values()) {
            sourceIds.add(chunk.getSourceId());
        }
        Map<Long, OaAiKbSource> sources = new HashMap<>();
        for (OaAiKbSource source : sourceMapper.selectBatchIds(sourceIds)) {
            sources.put(source.getId(), source);
        }
        for (Candidate candidate : ranked) {
            OaAiKbChunk chunk = chunks.get(candidate.chunkId);
            if (chunk == null) {
                continue;
            }
            OaAiKbSource source = sources.get(chunk.getSourceId());
            AiKbSearchHitVo hit = new AiKbSearchHitVo();
            hit.setChunkId(chunk.getId());
            hit.setSourceId(chunk.getSourceId());
            hit.setKbId(chunk.getKbId());
            if (source != null) {
                hit.setSourceType(source.getSourceType());
                hit.setDocId(source.getDocId());
                hit.setFileId(source.getFileId());
                hit.setTitle(source.getTitle());
                hit.setLink(linkOf(source));
            }
            hit.setHeading(chunk.getHeading());
            hit.setSnippet(snippetOf(chunk.getContent()));
            hit.setScore(candidate.displayScore);
            hit.setVectorScore(candidate.vectorScore == null ? null : (double) candidate.vectorScore);
            hits.add(hit);
        }
        return hits;
    }

    /** 跳转链接（docs/21 AI-M3-05 引用可跳转） */
    static String linkOf(OaAiKbSource source) {
        if (OaAiKbSource.TYPE_DOCUMENT.equals(source.getSourceType()) && source.getDocId() != null) {
            return "/knowledge/document?docId=" + source.getDocId();
        }
        if (source.getFileId() != null) {
            String keyword = source.getTitle() == null ? "" : java.net.URLEncoder.encode(source.getTitle(),
                java.nio.charset.StandardCharsets.UTF_8);
            return "/knowledge/files?keyword=" + keyword;
        }
        return null;
    }

    private static String snippetOf(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }
}
