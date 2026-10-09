package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.domain.bo.AiKbSourceBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQaRequestBo;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSourceVo;
import org.dromara.agentoa.ai.domain.vo.QaHistoryVo;
import org.dromara.agentoa.ai.domain.vo.QaResultVo;
import org.dromara.agentoa.ai.service.IAiQaService;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识问答契约（docs/21 §5.4）：引用溯源、检索权限过滤（越权知识域/文档不命中）、
 * 引用片段权限、会话隔离、SSE 事件序列。
 */
class AiQaRagH2Test {

    private Long chatModelId;

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.embeddingTransport.vectorOf =
            text -> (text != null && text.contains("苹果")) ? new float[] {1f, 0f} : new float[] {0f, 1f};
        var chatModel = AiTestEnvironment.seedProviderAndModel("对话渠道", "chat-test", "[\"chat\"]");
        chatModel.setIsDefault(1);
        AiTestEnvironment.models.updateById(chatModel);
        chatModelId = chatModel.getId();
    }

    private OaAiKb seedKb(String visibility) {
        var model = AiTestEnvironment.seedProviderAndModel("向量渠道", "embed-test", "[\"embedding\"]");
        return AiTestEnvironment.seedKb("知识域", visibility, model.getId(), AiTestEnvironment.USER_A);
    }

    private AiKbSourceVo addDocument(OaAiKb kb, Long docId, String title, String text, Set<Long> viewers) {
        AiTestEnvironment.documentAccess.put(docId, title, text, viewers);
        AiKbSourceBo bo = new AiKbSourceBo();
        bo.setSourceType(OaAiKbSource.TYPE_DOCUMENT);
        bo.setDocId(docId);
        AiKbSourceVo source = AiTestEnvironment.kbService.addDocumentSource(kb.getId(), bo,
            AiTestEnvironment.USER_A, "u100");
        AiTestEnvironment.indexer.awaitIdle();
        return source;
    }

    private AiQaRequestBo ask(String query, Long kbId) {
        AiQaRequestBo bo = new AiQaRequestBo();
        bo.setQuery(query);
        bo.setKbIds(kbId == null ? null : List.of(kbId));
        bo.setModelId(chatModelId);
        return bo;
    }

    private QaResultVo askAndWait(AiQaRequestBo bo, Long userId, CollectingListener listener) throws Exception {
        AiTestEnvironment.qaService.ask(bo, userId, "u" + userId, listener);
        assertThat(listener.latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(listener.error).isNull();
        return listener.result;
    }

    // ---------------------------------------------------------------- 引用溯源与事件序列

    @Test
    void askStreamsWithCitations() throws Exception {
        OaAiKb kb = seedKb(OaAiKb.VISIBILITY_ALL);
        addDocument(kb, 21L, "苹果制度", "苹果的保质期说明", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        CollectingListener listener = new CollectingListener();

        QaResultVo result = askAndWait(ask("苹果", kb.getId()), AiTestEnvironment.USER_B, listener);

        assertThat(listener.deltas).isNotEmpty();
        assertThat(result.getConversationId()).isNotNull();
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getIndex()).isEqualTo(1);
        assertThat(result.getCitations().get(0).getTitle()).isEqualTo("苹果制度");
        assertThat(result.getCitations().get(0).getSnippet()).contains("苹果");
        assertThat(result.getCitations().get(0).getLink()).isEqualTo("/knowledge/document?docId=21");
        // 引用随消息留存
        var message = AiTestEnvironment.messages.selectById(result.getMessageId());
        assertThat(message.getCitations()).contains("苹果制度");
    }

    // ---------------------------------------------------------------- 权限过滤

    @Test
    void searchFiltersDocumentsOutsideAskScope() {
        OaAiKb kb = seedKb(OaAiKb.VISIBILITY_ALL);
        addDocument(kb, 31L, "公开制度", "苹果公开规则", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        addDocument(kb, 32L, "机密制度", "苹果秘密规则", Set.of(AiTestEnvironment.USER_A));

        List<AiKbSearchHitVo> hitsOfB = AiTestEnvironment.kbService.searchTest(kb.getId(),
            searchQuery("苹果"), AiTestEnvironment.USER_B, "u200");
        assertThat(hitsOfB).isNotEmpty();
        assertThat(hitsOfB).allSatisfy(hit -> {
            assertThat(hit.getTitle()).isEqualTo("公开制度");
            assertThat(hit.getSnippet()).doesNotContain("秘密");
        });

        List<AiKbSearchHitVo> hitsOfA = AiTestEnvironment.kbService.searchTest(kb.getId(),
            searchQuery("苹果"), AiTestEnvironment.USER_A, "u100");
        assertThat(hitsOfA).hasSize(2);
    }

    @Test
    void citationsNeverLeakInvisibleDocuments() throws Exception {
        OaAiKb kb = seedKb(OaAiKb.VISIBILITY_ALL);
        addDocument(kb, 41L, "公开制度", "苹果公开规则", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        addDocument(kb, 42L, "机密制度", "苹果秘密规则", Set.of(AiTestEnvironment.USER_A));
        CollectingListener listener = new CollectingListener();

        QaResultVo result = askAndWait(ask("苹果", kb.getId()), AiTestEnvironment.USER_B, listener);

        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getTitle()).isEqualTo("公开制度");
        assertThat(result.getCitations().get(0).getSnippet()).doesNotContain("秘密");
    }

    @Test
    void crossKbAskIsRejected() {
        OaAiKb privateKb = seedKb(OaAiKb.VISIBILITY_PRIVATE);
        CollectingListener listener = new CollectingListener();

        assertThatThrownBy(() -> AiTestEnvironment.qaService.ask(ask("苹果", privateKb.getId()),
            AiTestEnvironment.USER_B, "u200", listener))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_KB_NOT_FOUND");

        assertThatThrownBy(() -> AiTestEnvironment.qaService.ask(ask("苹果", null),
            AiTestEnvironment.USER_B, "u200", listener))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QA_NO_KB");
    }

    @Test
    void historyIsIsolatedByOwner() throws Exception {
        OaAiKb kb = seedKb(OaAiKb.VISIBILITY_ALL);
        addDocument(kb, 51L, "苹果制度", "苹果的保质期说明", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        CollectingListener listener = new CollectingListener();
        askAndWait(ask("苹果", kb.getId()), AiTestEnvironment.USER_A, listener);

        AiPageQuery page = new AiPageQuery();
        var historyOfA = AiTestEnvironment.qaService.history(page, AiTestEnvironment.USER_A);
        var historyOfB = AiTestEnvironment.qaService.history(page, AiTestEnvironment.USER_B);

        assertThat(historyOfA.getRecords()).hasSize(1);
        QaHistoryVo record = historyOfA.getRecords().get(0);
        assertThat(record.getQuestion()).isEqualTo("苹果");
        assertThat(record.getAnswer()).isNotBlank();
        assertThat(record.getCitations()).hasSize(1);
        assertThat(historyOfB.getRecords()).isEmpty();
    }

    @Test
    void queryEmbeddingRunsPerKbEmbeddingModel() {
        // 异构向量模型（docs/21 AI-M3-04）：查询向量化按知识域绑定模型分组各查各的，
        // 回归：多知识域混检不再只取首个知识域的向量模型（跨模型相似度曾为静默错分）
        var modelA = AiTestEnvironment.seedProviderAndModel("向量甲", "embed-a", "[\"embedding\"]");
        var modelB = AiTestEnvironment.seedProviderAndModel("向量乙", "embed-b", "[\"embedding\"]");
        OaAiKb kbA = AiTestEnvironment.seedKb("知识甲", OaAiKb.VISIBILITY_ALL, modelA.getId(), AiTestEnvironment.USER_A);
        OaAiKb kbB = AiTestEnvironment.seedKb("知识乙", OaAiKb.VISIBILITY_ALL, modelB.getId(), AiTestEnvironment.USER_A);
        addDocument(kbA, 61L, "报销制度", "报销需在30天内提交", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        addDocument(kbB, 62L, "报销流程", "报销先走部门审批", Set.of(AiTestEnvironment.USER_A, AiTestEnvironment.USER_B));
        AiTestEnvironment.embeddingTransport.calls.clear();

        List<AiKbSearchHitVo> hits = AiTestEnvironment.retriever.search(AiTestEnvironment.USER_B, "u200",
            List.of(kbA, kbB), "报销", 5);

        assertThat(AiTestEnvironment.embeddingTransport.calls).hasSize(2);
        assertThat(AiTestEnvironment.embeddingTransport.calls)
            .extracting(call -> call.modelKey())
            .containsExactlyInAnyOrder("embed-a", "embed-b");
        assertThat(hits).hasSize(2);
    }

    private org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo searchQuery(String text) {
        org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo bo = new org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo();
        bo.setQuery(text);
        return bo;
    }

    /** 收集 SSE 事件（meta/delta/usage/done/error） */
    private static final class CollectingListener implements IAiQaService.StreamListener {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<String> deltas = new ArrayList<>();
        private volatile QaResultVo result;
        private volatile ServiceException error;

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public void onComplete(QaResultVo value) {
            result = value;
            latch.countDown();
        }

        @Override
        public void onError(ServiceException value) {
            error = value;
            latch.countDown();
        }
    }
}
