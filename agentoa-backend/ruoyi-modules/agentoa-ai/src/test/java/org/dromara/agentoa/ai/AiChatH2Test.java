package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.bo.AiChatRequestBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiChatResultVo;
import org.dromara.agentoa.ai.domain.vo.AiMessageVo;
import org.dromara.agentoa.ai.service.IAiChatService;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 对话契约（docs/21 §4.4）：会话/消息越权 404、停止生成状态机、重生成版本留存、
 * 非 vision 模型拒图、附件数量校验、流式事件序列。
 */
class AiChatH2Test {

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.rebuildQuotaGuard(100_000L, 500);
    }

    private OaAiModel defaultChatModel() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("对话渠道", "gpt-chat", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);
        return model;
    }

    private static final class CollectingListener implements IAiChatService.StreamListener {
        final List<String> deltas = new ArrayList<>();
        final AtomicReference<AiChatResultVo> result = new AtomicReference<>();
        final AtomicReference<ServiceException> error = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public void onComplete(AiChatResultVo result) {
            this.result.set(result);
            finished.countDown();
        }

        @Override
        public void onError(ServiceException error) {
            this.error.set(error);
            finished.countDown();
        }
    }

    // ---------------------------------------------------------------- 会话隔离

    @Test
    void otherUsersConversationsAreInvisible() {
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("第一句话");
        CollectingListener listener = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);
        await(listener);

        assertThatThrownBy(() -> AiTestEnvironment.chatService.conversation(handle.conversationId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_CONVERSATION_NOT_FOUND");
        assertThatThrownBy(() -> AiTestEnvironment.chatService.messages(handle.conversationId(), new AiPageQuery(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_CONVERSATION_NOT_FOUND");
        assertThatThrownBy(() -> AiTestEnvironment.chatService.stop(handle.messageId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_MESSAGE_NOT_FOUND");
        assertThat(AiTestEnvironment.chatService.conversations(new AiPageQuery(), AiTestEnvironment.USER_B).getRecords()).isEmpty();
    }

    // ---------------------------------------------------------------- 流式补全

    @Test
    void conversationBoundToDeletedModelFallsBackToDefault() {
        OaAiModel bound = AiTestEnvironment.seedProviderAndModel("绑定渠道", "gpt-bound", "[\"chat\"]");
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("第一句话");
        bo.setModelId(bound.getId());
        CollectingListener listener = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);
        await(listener);
        assertThat(listener.error.get()).isNull();

        AiTestEnvironment.models.deleteById(bound.getId());

        AiChatRequestBo next = new AiChatRequestBo();
        next.setConversationId(handle.conversationId());
        next.setContent("第二句话");
        CollectingListener retry = new CollectingListener();
        AiTestEnvironment.chatService.chat(next, AiTestEnvironment.USER_A, "u100", retry);
        await(retry);
        assertThat(retry.error.get()).isNull();

        AiChatRequestBo explicit = new AiChatRequestBo();
        explicit.setContent("第三句话");
        explicit.setModelId(bound.getId());
        CollectingListener strict = new CollectingListener();
        assertThatThrownBy(() -> AiTestEnvironment.chatService.chat(explicit, AiTestEnvironment.USER_A, "u100", strict))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_MODEL_NOT_FOUND");
    }

    @Test
    void chatStreamsPersistsMessagesAndAutoTitles() {
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("帮我写一封关于项目延期的邮件通知");
        CollectingListener listener = new CollectingListener();

        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);
        await(listener);

        assertThat(listener.error.get()).isNull();
        assertThat(listener.deltas).containsExactly("你好", "，世界");
        assertThat(listener.result.get().getContent()).isEqualTo("你好，世界");
        assertThat(listener.result.get().getMessageId()).isEqualTo(handle.messageId());

        var messages = AiTestEnvironment.chatService.messages(handle.conversationId(), new AiPageQuery(), AiTestEnvironment.USER_A);
        assertThat(messages.getRecords()).hasSize(2);
        AiMessageVo user = messages.getRecords().get(0);
        AiMessageVo assistant = messages.getRecords().get(1);
        assertThat(user.getRole()).isEqualTo("user");
        assertThat(user.getStatus()).isEqualTo("done");
        assertThat(assistant.getRole()).isEqualTo("assistant");
        assertThat(assistant.getStatus()).isEqualTo("done");
        assertThat(assistant.getContent()).isEqualTo("你好，世界");

        var conversation = AiTestEnvironment.chatService.conversation(handle.conversationId(), AiTestEnvironment.USER_A);
        assertThat(conversation.getTitle()).isEqualTo("帮我写一封关于项目延期的邮件通知");

        AiTestEnvironment.usageRecorder.flush();
        assertThat(AiTestEnvironment.usages.selectCount(null)).isEqualTo(1);
    }

    @Test
    void stopMarksMessageStoppedAndKeepsPartialContent() {
        defaultChatModel();
        AiTestEnvironment.transport.streamDeltas = List.of("第1段", "第2段", "第3段", "第4段");
        AiTestEnvironment.transport.streamDelayMs = 80L;

        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("长回答");
        CollectingListener listener = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);

        waitUntil(() -> !listener.deltas.isEmpty(), 5000);
        AiTestEnvironment.chatService.stop(handle.messageId(), AiTestEnvironment.USER_A);

        OaAiMessage message = AiTestEnvironment.messages.selectById(handle.messageId());
        assertThat(message.getStatus()).isEqualTo(OaAiMessage.STATUS_STOPPED);
        assertThat(message.getContent()).isNotBlank();
    }

    @Test
    void regenerateKeepsPreviousVersion() {
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("原始问题");
        CollectingListener first = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", first);
        await(first);

        AiTestEnvironment.transport.streamDeltas = List.of("新版本");
        CollectingListener second = new CollectingListener();
        IAiChatService.ChatHandle regenerated =
            AiTestEnvironment.chatService.regenerate(handle.messageId(), AiTestEnvironment.USER_A, "u100", second);
        await(second);

        assertThat(regenerated.messageId()).isNotEqualTo(handle.messageId());
        OaAiMessage oldMessage = AiTestEnvironment.messages.selectById(handle.messageId());
        assertThat(oldMessage.getStatus()).isEqualTo(OaAiMessage.STATUS_DONE);
        assertThat(oldMessage.getContent()).isEqualTo("你好，世界");

        var messages = AiTestEnvironment.chatService.messages(handle.conversationId(), new AiPageQuery(), AiTestEnvironment.USER_A);
        long assistantCount = messages.getRecords().stream().filter(m -> "assistant".equals(m.getRole())).count();
        assertThat(assistantCount).isEqualTo(2);
    }

    // ---------------------------------------------------------------- 多模态与附件

    @Test
    void nonVisionModelRejectsImages() {
        defaultChatModel();
        long fileId = AiTestEnvironment.imageStore.upload("a.png", pngBytes(), AiTestEnvironment.USER_A).fileId();

        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("这是什么");
        bo.setAttachmentIds(List.of(fileId));
        CollectingListener listener = new CollectingListener();
        assertThatThrownBy(() -> AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("MODEL_CAPABILITY_MISMATCH");
    }

    @Test
    void visionModelAcceptsImages() {
        OaAiModel vision = AiTestEnvironment.seedProviderAndModel("视觉渠道", "gpt-vision", "[\"chat\",\"vision\"]");
        vision.setIsDefault(1);
        AiTestEnvironment.models.updateById(vision);
        long fileId = AiTestEnvironment.imageStore.upload("a.png", pngBytes(), AiTestEnvironment.USER_A).fileId();

        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("这是什么");
        bo.setAttachmentIds(List.of(fileId));
        CollectingListener listener = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);
        await(listener);

        assertThat(listener.error.get()).isNull();
        assertThat(listener.deltas).isNotEmpty();
        var call = AiTestEnvironment.transport.calls.get(0);
        assertThat(call.turns().get(call.turns().size() - 1).images()).hasSize(1);
    }

    @Test
    void unknownAttachmentIs404() {
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("图");
        bo.setAttachmentIds(List.of(999999L));
        CollectingListener listener = new CollectingListener();
        assertThatThrownBy(() -> AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_NOT_FOUND");
    }

    @Test
    void attachmentCountIsLimited() {
        defaultChatModel();
        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("图");
        bo.setAttachmentIds(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        CollectingListener listener = new CollectingListener();
        assertThatThrownBy(() -> AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_COUNT");
    }

    // ---------------------------------------------------------------- 限额与模板

    @Test
    void midStreamQuotaExhaustionMarksMessageError() {
        defaultChatModel();
        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("user");
        quota.setScopeId(AiTestEnvironment.USER_A);
        quota.setPeriodType("day");
        quota.setTokenLimit(800L);
        quota.setRequestLimit(100);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);
        // 首个 delta 1000 token：调用前预算（输入 + 预留 512）≤ 800 放行，产出后触发中途熔断
        AiTestEnvironment.transport.streamDeltas = List.of("一".repeat(1000), "二".repeat(1000));

        AiChatRequestBo bo = new AiChatRequestBo();
        bo.setContent("长回答");
        CollectingListener listener = new CollectingListener();
        IAiChatService.ChatHandle handle = AiTestEnvironment.chatService.chat(bo, AiTestEnvironment.USER_A, "u100", listener);
        await(listener);

        assertThat(listener.error.get()).isNotNull();
        assertThat(listener.error.get().getMessage())
            .contains("AI_QUOTA_EXCEEDED").contains("流式生成已中断");
        assertThat(listener.deltas).hasSize(1);
        OaAiMessage message = AiTestEnvironment.messages.selectById(handle.messageId());
        assertThat(message.getStatus()).isEqualTo(OaAiMessage.STATUS_ERROR);
        assertThat(message.getErrorCode()).isEqualTo("AI_QUOTA_EXCEEDED");
        assertThat(message.getContent()).isNotBlank();
    }

    @Test
    void chatTemplateListHidesCopilotSceneTemplates() {
        org.dromara.agentoa.ai.domain.OaAiPromptTemplate chatTemplate = new org.dromara.agentoa.ai.domain.OaAiPromptTemplate();
        chatTemplate.setCode("mail_polish");
        chatTemplate.setName("邮件润色");
        chatTemplate.setContent("润色邮件");
        chatTemplate.setEnabled(1);
        chatTemplate.setIsBuiltin(1);
        AiTestEnvironment.templates.insert(chatTemplate);

        org.dromara.agentoa.ai.domain.OaAiPromptTemplate sceneTemplate = new org.dromara.agentoa.ai.domain.OaAiPromptTemplate();
        sceneTemplate.setCode("copilot_report");
        sceneTemplate.setName("报表解读");
        sceneTemplate.setContent("解读报表");
        sceneTemplate.setEnabled(1);
        sceneTemplate.setIsBuiltin(1);
        AiTestEnvironment.templates.insert(sceneTemplate);

        org.dromara.agentoa.ai.domain.OaAiCopilotConfig config = new org.dromara.agentoa.ai.domain.OaAiCopilotConfig();
        config.setSceneCode("report-insight");
        config.setSceneName("报表解读");
        config.setEnabled(1);
        config.setPromptTemplateId(sceneTemplate.getId());
        AiTestEnvironment.copilotConfigs.insert(config);

        // 会话模板选择器不出现 Copilot 场景专用模板
        var enabled = AiTestEnvironment.promptService.listEnabled();
        assertThat(enabled).extracting(template -> template.getCode()).containsExactly("mail_polish");
        // 管理端仍可见全量
        var adminPage = AiTestEnvironment.promptService.page(new AiPageQuery());
        assertThat(adminPage.getRecords()).extracting(template -> template.getCode())
            .contains("mail_polish", "copilot_report");
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] pngBytes() {
        return new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13};
    }

    private static void await(CollectingListener listener) {
        try {
            assertThat(listener.finished.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("condition not met in " + timeoutMs + "ms");
    }
}
