package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiConversation;
import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiPromptTemplate;
import org.dromara.agentoa.ai.domain.bo.AiChatRequestBo;
import org.dromara.agentoa.ai.domain.bo.AiConversationBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.enums.AiBizType;
import org.dromara.agentoa.ai.domain.enums.AiCapability;
import org.dromara.agentoa.ai.domain.vo.AiChatResultVo;
import org.dromara.agentoa.ai.domain.vo.AiConversationVo;
import org.dromara.agentoa.ai.domain.vo.AiMessageVo;
import org.dromara.agentoa.ai.mapper.OaAiConversationMapper;
import org.dromara.agentoa.ai.mapper.OaAiMessageMapper;
import org.dromara.agentoa.ai.service.IAiChatService;
import org.dromara.agentoa.ai.service.IAiPromptTemplateService;
import org.dromara.agentoa.ai.service.support.AiAttachmentRules;
import org.dromara.agentoa.ai.service.support.AiImageStore;
import org.dromara.agentoa.ai.service.support.AiModelRouter;
import org.dromara.agentoa.ai.service.support.ChatContextBuilder;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 对话实现（docs/21 M2）：会话/消息按账号隔离（他人会话 404）；
 * 流式输出逐段回传，停止生成取消上游输出并置 stopped，重生成保留旧版本。
 */
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements IAiChatService {

    private final OaAiConversationMapper conversationMapper;
    private final OaAiMessageMapper messageMapper;
    private final IAiPromptTemplateService promptTemplateService;
    private final AiImageStore imageStore;
    private final AiModelRouter modelRouter;
    private final LlmGateway llmGateway;

    /** 进行中的流式会话（assistant 消息 ID → 会话上下文） */
    private final Map<Long, StreamSession> activeStreams = new ConcurrentHashMap<>();

    private static final class StreamSession {
        private volatile LlmGateway.StreamTicket ticket;
        private final StringBuilder buffer = new StringBuilder();
        private final OaAiConversation conversation;
        private final OaAiMessage assistantMessage;

        private StreamSession(OaAiConversation conversation, OaAiMessage assistantMessage) {
            this.conversation = conversation;
            this.assistantMessage = assistantMessage;
        }
    }

    // ---------------------------------------------------------------- conversations

    @Override
    public PageVo<AiConversationVo> conversations(AiPageQuery page, Long userId) {
        IPage<OaAiConversation> result = conversationMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiConversation>()
                .eq(OaAiConversation::getUserId, userId)
                .eq(OaAiConversation::getStatus, OaAiConversation.STATUS_ACTIVE)
                .eq(OaAiConversation::getScene, OaAiConversation.SCENE_CHAT)
                .orderByDesc(OaAiConversation::getLastMessageTime)
                .orderByDesc(OaAiConversation::getId));
        List<AiConversationVo> records = new ArrayList<>();
        for (OaAiConversation conversation : result.getRecords()) {
            records.add(toVo(conversation));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiConversationVo conversation(Long id, Long userId) {
        return toVo(requireOwned(id, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationVo createConversation(AiConversationBo bo, Long userId) {
        OaAiConversation conversation = new OaAiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(bo == null || bo.getTitle() == null || bo.getTitle().isBlank()
            ? "新对话" : bo.getTitle().trim());
        if (bo != null) {
            conversation.setModelId(bo.getModelId());
            conversation.setPromptTemplateId(bo.getPromptTemplateId());
        }
        conversation.setStatus(OaAiConversation.STATUS_ACTIVE);
        conversation.setScene(OaAiConversation.SCENE_CHAT);
        conversationMapper.insert(conversation);
        return toVo(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationVo updateConversation(Long id, AiConversationBo bo, Long userId) {
        OaAiConversation conversation = requireOwned(id, userId);
        if (bo.getTitle() != null && !bo.getTitle().isBlank()) {
            conversation.setTitle(bo.getTitle().trim());
        }
        // 模型切换只影响新消息（docs/21 AI-M2-06）
        if (bo.getModelId() != null) {
            conversation.setModelId(bo.getModelId());
        }
        if (bo.getPromptTemplateId() != null) {
            conversation.setPromptTemplateId(bo.getPromptTemplateId());
        }
        conversationMapper.updateById(conversation);
        return toVo(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long id, Long userId) {
        OaAiConversation conversation = requireOwned(id, userId);
        conversation.setStatus(OaAiConversation.STATUS_DELETED);
        conversationMapper.updateById(conversation);
    }

    @Override
    public PageVo<AiMessageVo> messages(Long conversationId, AiPageQuery page, Long userId) {
        requireOwned(conversationId, userId);
        IPage<OaAiMessage> result = messageMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiMessage>()
                .eq(OaAiMessage::getConversationId, conversationId)
                .orderByAsc(OaAiMessage::getId));
        List<AiMessageVo> records = new ArrayList<>();
        for (OaAiMessage message : result.getRecords()) {
            records.add(toVo(message));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    // ---------------------------------------------------------------- chat

    @Override
    public ChatHandle chat(AiChatRequestBo bo, Long userId, String username, StreamListener listener) {
        AiAttachmentRules.validateCount(bo.getAttachmentIds());
        OaAiConversation existing = bo.getConversationId() == null ? null : requireOwned(bo.getConversationId(), userId);
        Long modelId = bo.getModelId() != null ? bo.getModelId()
            : existing == null ? null : existing.getModelId();

        List<AiModelRouter.Route> routes = bo.getModelId() == null
            ? routesForConversation(modelId) : modelRouter.candidates(modelId);
        AiModelRouter.Route anchor = routes.get(0);
        List<ChatTransport.Image> images = loadImages(bo.getAttachmentIds(), userId);
        ensureVision(anchor.model(), images);

        OaAiConversation conversation = existing == null ? newConversation(userId, bo) : existing;
        boolean changed = false;
        if (bo.getModelId() != null && !bo.getModelId().equals(conversation.getModelId())) {
            conversation.setModelId(bo.getModelId());
            changed = true;
        }
        if (bo.getPromptTemplateId() != null
            && !bo.getPromptTemplateId().equals(conversation.getPromptTemplateId())) {
            conversation.setPromptTemplateId(bo.getPromptTemplateId());
            changed = true;
        }
        if (changed) {
            conversationMapper.updateById(conversation);
        }

        OaAiMessage userMessage = insertMessage(conversation, OaAiMessage.ROLE_USER, bo.getContent(),
            bo.getAttachmentIds(), anchor.model().getId(), OaAiMessage.STATUS_DONE);
        List<OaAiMessage> history = historyBefore(conversation.getId(), userMessage.getId());
        OaAiMessage assistantMessage = insertMessage(conversation, OaAiMessage.ROLE_ASSISTANT, "", null,
            anchor.model().getId(), OaAiMessage.STATUS_STREAMING);

        ChatTransport.Turn current = ChatTransport.Turn.user(bo.getContent(), images);
        List<ChatTransport.Turn> turns = ChatContextBuilder.build(
            systemPromptOf(conversation), history, current,
            anchor.model().getContextWindow(), anchor.model().getMaxTokens());

        return startStream(conversation, assistantMessage, turns, anchor.model(), userId, username, listener);
    }

    @Override
    public void stop(Long messageId, Long userId) {
        OaAiMessage message = requireOwnedMessage(messageId, userId);
        if (!OaAiMessage.STATUS_STREAMING.equals(message.getStatus())) {
            return;
        }
        StreamSession session = activeStreams.remove(messageId);
        if (session != null && session.ticket != null) {
            session.ticket.cancel();
            message.setContent(session.buffer.toString());
        }
        message.setStatus(OaAiMessage.STATUS_STOPPED);
        messageMapper.updateById(message);
    }

    @Override
    public ChatHandle regenerate(Long messageId, Long userId, String username, StreamListener listener) {
        OaAiMessage oldAssistant = requireOwnedMessage(messageId, userId);
        if (!OaAiMessage.ROLE_ASSISTANT.equals(oldAssistant.getRole())) {
            throw new ServiceException("AI_MESSAGE_NOT_ASSISTANT 只能重新生成助手消息", 400);
        }
        OaAiConversation conversation = requireOwned(oldAssistant.getConversationId(), userId);
        OaAiMessage userMessage = null;
        List<OaAiMessage> before = messageMapper.selectList(new LambdaQueryWrapper<OaAiMessage>()
            .eq(OaAiMessage::getConversationId, conversation.getId())
            .lt(OaAiMessage::getId, oldAssistant.getId())
            .orderByAsc(OaAiMessage::getId));
        for (OaAiMessage message : before) {
            if (OaAiMessage.ROLE_USER.equals(message.getRole())) {
                userMessage = message;
            }
        }
        if (userMessage == null) {
            throw new ServiceException("AI_MESSAGE_NOT_FOUND 没有可重算的用户消息", 404);
        }
        List<OaAiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<OaAiMessage>()
            .eq(OaAiMessage::getConversationId, conversation.getId())
            .lt(OaAiMessage::getId, userMessage.getId())
            .orderByAsc(OaAiMessage::getId));

        List<ChatTransport.Image> images = loadImages(parseAttachments(userMessage.getAttachments()), userId);
        List<AiModelRouter.Route> routes = routesForConversation(conversation.getModelId());
        AiModelRouter.Route anchor = routes.get(0);
        ensureVision(anchor.model(), images);

        OaAiMessage assistantMessage = insertMessage(conversation, OaAiMessage.ROLE_ASSISTANT, "", null,
            anchor.model().getId(), OaAiMessage.STATUS_STREAMING);
        ChatTransport.Turn current = ChatTransport.Turn.user(userMessage.getContent(), images);
        List<ChatTransport.Turn> turns = ChatContextBuilder.build(
            systemPromptOf(conversation), history, current,
            anchor.model().getContextWindow(), anchor.model().getMaxTokens());
        return startStream(conversation, assistantMessage, turns, anchor.model(), userId, username, listener);
    }

    // ---------------------------------------------------------------- stream plumbing

    private ChatHandle startStream(OaAiConversation conversation, OaAiMessage assistantMessage,
                                   List<ChatTransport.Turn> turns, OaAiModel model,
                                   Long userId, String username, StreamListener listener) {
        LlmGateway.CompletionRequest request = new LlmGateway.CompletionRequest(
            userId, username, AiBizType.CHAT.code(), conversation.getId(), null,
            model.getId(), null, model.getMaxTokens(), turns);

        StreamSession session = new StreamSession(conversation, assistantMessage);
        activeStreams.put(assistantMessage.getId(), session);

        LlmGateway.StreamTicket ticket = llmGateway.stream(request, new LlmGateway.StreamListener() {
            @Override
            public void onDelta(String delta) {
                session.buffer.append(delta);
                listener.onDelta(delta);
            }

            @Override
            public void onComplete(LlmGateway.CompletionResult result) {
                activeStreams.remove(assistantMessage.getId());
                assistantMessage.setContent(result.text().isBlank() ? session.buffer.toString() : result.text());
                assistantMessage.setPromptTokens(result.promptTokens());
                assistantMessage.setCompletionTokens(result.completionTokens());
                assistantMessage.setStatus(OaAiMessage.STATUS_DONE);
                messageMapper.updateById(assistantMessage);
                touchConversation(conversation, turns);
                AiChatResultVo vo = new AiChatResultVo();
                vo.setConversationId(conversation.getId());
                vo.setMessageId(assistantMessage.getId());
                vo.setContent(assistantMessage.getContent());
                vo.setPromptTokens(result.promptTokens());
                vo.setCompletionTokens(result.completionTokens());
                vo.setStatus(OaAiMessage.STATUS_DONE);
                listener.onComplete(vo);
            }

            @Override
            public void onError(ServiceException error) {
                StreamSession current = activeStreams.remove(assistantMessage.getId());
                assistantMessage.setContent(current == null ? "" : current.buffer.toString());
                assistantMessage.setErrorCode(errorCodeOf(error));
                assistantMessage.setStatus(current != null && current.ticket != null && current.ticket.isCancelled()
                    ? OaAiMessage.STATUS_STOPPED : OaAiMessage.STATUS_ERROR);
                messageMapper.updateById(assistantMessage);
                listener.onError(error);
            }
        });
        session.ticket = ticket;
        return new ChatHandle() {
            @Override
            public void cancel() {
                ticket.cancel();
            }

            @Override
            public Long conversationId() {
                return conversation.getId();
            }

            @Override
            public Long messageId() {
                return assistantMessage.getId();
            }
        };
    }

    private void touchConversation(OaAiConversation conversation, List<ChatTransport.Turn> turns) {
        conversation.setLastMessageTime(new Date());
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()
            || "新对话".equals(conversation.getTitle())) {
            conversation.setTitle(autoTitle(turns));
        }
        conversationMapper.updateById(conversation);
    }

    private String autoTitle(List<ChatTransport.Turn> turns) {
        for (ChatTransport.Turn turn : turns) {
            if ("user".equals(turn.role()) && turn.text() != null && !turn.text().isBlank()) {
                String title = turn.text().replaceAll("\\s+", " ").trim();
                return title.length() > 24 ? title.substring(0, 24) : title;
            }
        }
        return "新对话";
    }

    private void ensureVision(OaAiModel model, List<ChatTransport.Image> images) {
        if (!images.isEmpty() && !AiCapability.supports(model.getCapability(), AiCapability.VISION.code())) {
            throw new ServiceException("MODEL_CAPABILITY_MISMATCH 当前模型不支持图片输入", 400);
        }
    }

    // ---------------------------------------------------------------- internals

    private List<AiModelRouter.Route> routesForConversation(Long modelId) {
        if (modelId == null) {
            return modelRouter.candidates(null);
        }
        try {
            return modelRouter.candidates(modelId);
        } catch (ServiceException e) {
            return modelRouter.candidates(null);
        }
    }

    private OaAiConversation newConversation(Long userId, AiChatRequestBo bo) {
        OaAiConversation conversation = new OaAiConversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setModelId(bo.getModelId());
        conversation.setPromptTemplateId(bo.getPromptTemplateId());
        conversation.setStatus(OaAiConversation.STATUS_ACTIVE);
        conversation.setScene(OaAiConversation.SCENE_CHAT);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private OaAiConversation requireOwned(Long conversationId, Long userId) {
        OaAiConversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);
        if (conversation == null
            || !userId.equals(conversation.getUserId())
            || !OaAiConversation.STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new ServiceException("AI_CONVERSATION_NOT_FOUND 会话不存在", 404);
        }
        return conversation;
    }

    private OaAiMessage requireOwnedMessage(Long messageId, Long userId) {
        OaAiMessage message = messageId == null ? null : messageMapper.selectById(messageId);
        if (message == null) {
            throw new ServiceException("AI_MESSAGE_NOT_FOUND 消息不存在", 404);
        }
        OaAiConversation conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null || !userId.equals(conversation.getUserId())
            || !OaAiConversation.STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new ServiceException("AI_MESSAGE_NOT_FOUND 消息不存在", 404);
        }
        return message;
    }

    private OaAiMessage insertMessage(OaAiConversation conversation, String role, String content,
                                      List<Long> attachmentIds, Long modelId, String status) {
        OaAiMessage message = new OaAiMessage();
        message.setConversationId(conversation.getId());
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setAttachments(attachmentIds == null || attachmentIds.isEmpty() ? null : JsonUtils.toJsonString(attachmentIds));
        message.setModelId(modelId);
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setStatus(status);
        messageMapper.insert(message);
        return message;
    }

    private List<OaAiMessage> historyBefore(Long conversationId, Long messageId) {
        return messageMapper.selectList(new LambdaQueryWrapper<OaAiMessage>()
            .eq(OaAiMessage::getConversationId, conversationId)
            .lt(OaAiMessage::getId, messageId)
            .orderByAsc(OaAiMessage::getId));
    }

    private String systemPromptOf(OaAiConversation conversation) {
        if (conversation.getPromptTemplateId() == null) {
            return null;
        }
        OaAiPromptTemplate template = promptTemplateService.require(conversation.getPromptTemplateId());
        return template.getEnabled() != null && template.getEnabled() == 1 ? template.getContent() : null;
    }

    private List<ChatTransport.Image> loadImages(List<Long> attachmentIds, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        AiAttachmentRules.validateCount(attachmentIds);
        List<ChatTransport.Image> images = new ArrayList<>();
        for (Long fileId : attachmentIds) {
            AiImageStore.LoadedImage loaded = imageStore.read(fileId, userId);
            images.add(new ChatTransport.Image(loaded.mimeType(), loaded.bytes()));
        }
        return images;
    }

    private List<Long> parseAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isBlank()) {
            return List.of();
        }
        List<Long> ids = JsonUtils.parseArray(attachmentsJson, Long.class);
        return ids == null ? List.of() : ids;
    }

    private String errorCodeOf(ServiceException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "LLM_UPSTREAM_ERROR";
        }
        int space = message.indexOf(' ');
        return space > 0 ? message.substring(0, space) : message;
    }

    private AiConversationVo toVo(OaAiConversation conversation) {
        AiConversationVo vo = new AiConversationVo();
        vo.setId(conversation.getId());
        vo.setUserId(conversation.getUserId());
        vo.setTitle(conversation.getTitle());
        vo.setModelId(conversation.getModelId());
        vo.setPromptTemplateId(conversation.getPromptTemplateId());
        vo.setStatus(conversation.getStatus());
        vo.setLastMessageTime(conversation.getLastMessageTime());
        vo.setCreateTime(conversation.getCreateTime());
        return vo;
    }

    private AiMessageVo toVo(OaAiMessage message) {
        AiMessageVo vo = new AiMessageVo();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setAttachments(parseAttachments(message.getAttachments()));
        vo.setModelId(message.getModelId());
        vo.setPromptTokens(message.getPromptTokens());
        vo.setCompletionTokens(message.getCompletionTokens());
        vo.setStatus(message.getStatus());
        vo.setErrorCode(message.getErrorCode());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
