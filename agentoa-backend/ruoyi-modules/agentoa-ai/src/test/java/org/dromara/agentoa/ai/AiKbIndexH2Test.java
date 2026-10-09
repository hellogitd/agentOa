package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.domain.bo.AiKbBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSourceBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiKbChunkVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSourceVo;
import org.dromara.agentoa.ai.service.support.KbSourceSyncDispatcher;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 索引管线契约（docs/21 §5.4）：分块入库、增量重索引、索引失败可重试、
 * 删除源后不可检索、embedding 模型未配置报错、文档变更对账。
 */
class AiKbIndexH2Test {

    private static KbSourceSyncDispatcher syncDispatcher;

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
        syncDispatcher = new KbSourceSyncDispatcher(AiTestEnvironment.kbSources, AiTestEnvironment.kbChunks,
            AiTestEnvironment.documentAccess, AiTestEnvironment.indexer);
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.embeddingTransport.vectorOf =
            text -> (text != null && text.contains("苹果")) ? new float[] {1f, 0f} : new float[] {0f, 1f};
    }

    private OaAiKb seedKbWithEmbeddingModel() {
        var model = AiTestEnvironment.seedProviderAndModel("向量渠道", "embed-test", "[\"embedding\"]");
        return AiTestEnvironment.seedKb("测试知识域", OaAiKb.VISIBILITY_PRIVATE, model.getId(), AiTestEnvironment.USER_A);
    }

    private AiKbSearchTestBo query(String text) {
        AiKbSearchTestBo bo = new AiKbSearchTestBo();
        bo.setQuery(text);
        bo.setTopK(5);
        return bo;
    }

    // ---------------------------------------------------------------- 文件来源索引

    @Test
    void indexesFileSourceAndStaysSearchable() {
        OaAiKb kb = seedKbWithEmbeddingModel();

        AiKbSourceVo source = AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        OaAiKbSource stored = AiTestEnvironment.kbSources.selectById(source.getId());
        assertThat(stored.getIndexStatus()).isEqualTo(OaAiKbSource.STATUS_READY);
        assertThat(stored.getChunkCount()).isGreaterThanOrEqualTo(1);

        List<AiKbSearchHitVo> hits = AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100");
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getTitle()).isEqualTo("note.txt");
        assertThat(hits.get(0).getSnippet()).contains("苹果");
        assertThat(hits.get(0).getLink()).startsWith("/knowledge/files?keyword=");
    }

    @Test
    void reindexReplacesChunksAfterContentChange() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiKbSourceVo source = AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        AiTestEnvironment.kbFileStore.overwrite(source.getFileId(), "香蕉的储存方法".getBytes(StandardCharsets.UTF_8));
        AiTestEnvironment.kbService.reindex(kb.getId(), source.getId(), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("香蕉"),
            AiTestEnvironment.USER_A, "u100")).isNotEmpty();
        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100")).isEmpty();
    }

    @Test
    void syncDispatcherReindexesChangedDocumentsAndInvalidatesDeleted() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiTestEnvironment.documentAccess.put(11L, "制度文档", "苹果规则", Set.of(AiTestEnvironment.USER_A));
        AiKbSourceBo bo = new AiKbSourceBo();
        bo.setSourceType(OaAiKbSource.TYPE_DOCUMENT);
        bo.setDocId(11L);
        AiKbSourceVo source = AiTestEnvironment.kbService.addDocumentSource(kb.getId(), bo,
            AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        List<AiKbSearchHitVo> hits = AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100");
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getLink()).isEqualTo("/knowledge/document?docId=11");

        // 新版本触发增量重索引
        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        AiTestEnvironment.documentAccess.updateText(11L, "香蕉规则");
        syncDispatcher.syncOnce();
        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("香蕉"),
            AiTestEnvironment.USER_A, "u100")).isNotEmpty();

        // 删除触发失效
        AiTestEnvironment.documentAccess.remove(11L);
        syncDispatcher.syncOnce();
        OaAiKbSource stored = AiTestEnvironment.kbSources.selectById(source.getId());
        assertThat(stored.getIndexStatus()).isEqualTo(OaAiKbSource.STATUS_FAILED);
        assertThat(stored.getErrorMsg()).contains("已删除");
        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("香蕉"),
            AiTestEnvironment.USER_A, "u100")).isEmpty();
    }

    @Test
    void indexFailureMarksFailedAndRetrySucceeds() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiTestEnvironment.embeddingTransport.errorHandler =
            texts -> new org.dromara.agentoa.ai.service.support.LlmCallException(
                org.dromara.agentoa.ai.service.support.LlmCallException.Category.UPSTREAM, "boom");

        AiKbSourceVo source = AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        OaAiKbSource failed = AiTestEnvironment.kbSources.selectById(source.getId());
        assertThat(failed.getIndexStatus()).isEqualTo(OaAiKbSource.STATUS_FAILED);
        assertThat(failed.getErrorMsg()).contains("boom");

        AiTestEnvironment.embeddingTransport.errorHandler = null;
        AiTestEnvironment.kbService.reindex(kb.getId(), source.getId(), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        OaAiKbSource retried = AiTestEnvironment.kbSources.selectById(source.getId());
        assertThat(retried.getIndexStatus()).isEqualTo(OaAiKbSource.STATUS_READY);
        assertThat(retried.getErrorMsg()).isNull();
    }

    @Test
    void deletedSourceIsNotSearchable() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiKbSourceVo source = AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        AiTestEnvironment.kbService.removeSource(kb.getId(), source.getId(), AiTestEnvironment.USER_A);

        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100")).isEmpty();
    }

    @Test
    void missingEmbeddingModelThrows() {
        OaAiKb kb = AiTestEnvironment.seedKb("无向量模型", OaAiKb.VISIBILITY_PRIVATE, null, AiTestEnvironment.USER_A);
        byte[] bytes = "苹果".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt", bytes,
            AiTestEnvironment.USER_A, "u100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("EMBEDDING_MODEL_UNAVAILABLE");
        assertThatThrownBy(() -> AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("EMBEDDING_MODEL_UNAVAILABLE");
    }

    @Test
    void searchSurfacesEmbeddingFailureBeforeVisibilityShortCircuit() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        // 可见源为空（未建任何数据源）：查询向量化仍必须执行并如实暴露上游故障
        AiTestEnvironment.embeddingTransport.errorHandler =
            texts -> new org.dromara.agentoa.ai.service.support.LlmCallException(
                org.dromara.agentoa.ai.service.support.LlmCallException.Category.UPSTREAM, "embed boom");

        assertThatThrownBy(() -> AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LLM_UPSTREAM_ERROR");

        // 上游恢复后空知识域返回空命中（不报错）
        AiTestEnvironment.embeddingTransport.errorHandler = null;
        assertThat(AiTestEnvironment.kbService.searchTest(kb.getId(), query("苹果"),
            AiTestEnvironment.USER_A, "u100")).isEmpty();
    }

    @Test
    void chunkPreviewExposesChunks() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiKbSourceVo source = AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        AiPageQuery page = new AiPageQuery();
        var chunks = AiTestEnvironment.kbService.chunks(kb.getId(), source.getId(), page, AiTestEnvironment.USER_A);

        assertThat(chunks.getRecords()).isNotEmpty();
        AiKbChunkVo chunk = chunks.getRecords().get(0);
        assertThat(chunk.getSourceId()).isEqualTo(source.getId());
        assertThat(chunk.getSeq()).isZero();
        assertThat(chunk.getContent()).contains("苹果");
    }

    @Test
    void nonOwnerCannotMutateKb() {
        var model = AiTestEnvironment.seedProviderAndModel("向量渠道", "embed-test", "[\"embedding\"]");
        OaAiKb kb = AiTestEnvironment.seedKb("全员知识域", OaAiKb.VISIBILITY_ALL, model.getId(), AiTestEnvironment.USER_A);
        AiKbBo bo = new AiKbBo();
        bo.setName("改名");

        assertThatThrownBy(() -> AiTestEnvironment.kbService.update(kb.getId(), bo, AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_KB_FORBIDDEN");
        assertThatThrownBy(() -> AiTestEnvironment.kbService.delete(kb.getId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_KB_FORBIDDEN");
    }

    @Test
    void listWithoutNameFilterReturnsVisibleKbs() {
        var model = AiTestEnvironment.seedProviderAndModel("向量渠道", "embed-test", "[\"embedding\"]");
        AiTestEnvironment.seedKb("可见知识域", OaAiKb.VISIBILITY_ALL, model.getId(), AiTestEnvironment.USER_A);
        AiTestEnvironment.seedKb("他人私有域", OaAiKb.VISIBILITY_PRIVATE, model.getId(), AiTestEnvironment.USER_B);

        var page = AiTestEnvironment.kbService.list(new AiKbBo(), new AiPageQuery(), AiTestEnvironment.USER_A);
        assertThat(page.getRecords()).extracting(kb -> kb.getName()).containsExactly("可见知识域");
    }

    @Test
    void listExposesSourceAndChunkCounts() {
        OaAiKb kb = seedKbWithEmbeddingModel();
        AiTestEnvironment.kbService.addFileSource(kb.getId(), "note.txt",
            "苹果的保质期说明".getBytes(StandardCharsets.UTF_8), AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();

        var page = AiTestEnvironment.kbService.list(new AiKbBo(), new AiPageQuery(), AiTestEnvironment.USER_A);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getSourceCount()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getChunkCount()).isGreaterThanOrEqualTo(1);
    }
}
