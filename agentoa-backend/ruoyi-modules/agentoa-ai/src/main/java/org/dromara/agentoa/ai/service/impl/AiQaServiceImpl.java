package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiConversation;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQaRequestBo;
import org.dromara.agentoa.ai.domain.enums.AiBizType;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.QaCitationVo;
import org.dromara.agentoa.ai.domain.vo.QaHistoryVo;
import org.dromara.agentoa.ai.domain.vo.QaResultVo;
import org.dromara.agentoa.ai.mapper.OaAiConversationMapper;
import org.dromara.agentoa.ai.mapper.OaAiMessageMapper;
import org.dromara.agentoa.ai.service.IAiKbService;
import org.dromara.agentoa.ai.service.IAiQaService;
import org.dromara.agentoa.ai.service.support.ChatContextBuilder;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.ai.service.support.KbRetriever;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识问答实现（docs/21 M3）：混合检索 → 提示词注入 → 流式回答 → 引用溯源。
 * <p>
 * 会话按账号隔离（他人会话 404）；引用片段仅来自检索时可见的数据源，不越权泄露原文。
 */
@Service
@RequiredArgsConstructor
public class AiQaServiceImpl implements IAiQaService {

    /** 检索注入条数（docs/21 AI-M3-05 默认 top-k 5） */
    public static final int RETRIEVE_TOP_K = 5;

    private static final String SYSTEM_PROMPT = "你是企业知识库问答助手。请仅依据【参考资料】回答问题；"
        + "资料不足时明确说明不知道，不要编造。回答中引用资料时标注对应序号，如 [1]。"
        + "AI 生成内容仅供参考，请人工核对后使用。";

    private final OaAiConversationMapper conversationMapper;
    private final OaAiMessageMapper messageMapper;
    private final IAiKbService kbService;
    private final KbRetriever retriever;
    private final LlmGateway llmGateway;

    /** 进行中的流式会话（assistant 消息 ID → 会话上下文） */
    private final Map<Long, QaSession> activeStreams = new ConcurrentHashMap<>();

    private static final class QaSession {
        private volatile LlmGateway.StreamTicket ticket;
        private final StringBuilder buffer = new StringBuilder();
    }

    @Override
    public QaHandle ask(AiQaRequestBo bo, Long userId, String username, StreamListener listener) {
        List<OaAiKb> kbs = kbService.visibleKbs(userId, bo.getKbIds());
        if (kbs.isEmpty()) {
            throw new ServiceException("AI_QA_NO_KB 没有可用知识域", 400);
        }
        List<AiKbSearchHitVo> hits =
            retriever.search(userId, username, kbs, bo.getQuery().strip(), RETRIEVE_TOP_K);

        OaAiConversation conversation = bo.getConversationId() == null
            ? newConversation(userId, bo) : requireOwnedQaConversation(bo.getConversationId(), userId);
        OaAiMessage userMessage = insertMessage(conversation, OaAiMessage.ROLE_USER, bo.getQuery(), null,
            OaAiMessage.STATUS_DONE);
        List<OaAiMessage> history = historyBefore(conversation.getId(), userMessage.getId());
        OaAiMessage assistantMessage = insertMessage(conversation, OaAiMessage.ROLE_ASSISTANT, "", null,
            OaAiMessage.STATUS_STREAMING);

        List<ChatTransport.Turn> turns = buildTurns(hits, bo.getQuery(), history);
        return startStream(conversation, assistantMessage, turns, hits, userId, username, listener);
    }

    @Override
    public PageVo<QaHistoryVo> history(AiPageQuery page, Long userId) {
        List<Long> conversationIds = conversationMapper.selectList(new LambdaQueryWrapper<OaAiConversation>()
            .select(OaAiConversation::getId)
            .eq(OaAiConversation::getUserId, userId)
            .eq(OaAiConversation::getStatus, OaAiConversation.STATUS_ACTIVE)
            .eq(OaAiConversation::getScene, OaAiConversation.SCENE_QA)).stream()
            .map(OaAiConversation::getId).toList();
        if (conversationIds.isEmpty()) {
            return PageVo.of(List.of(), 0, page.safePageNum(), page.safePageSize());
        }
        IPage<OaAiMessage> result = messageMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiMessage>()
                .eq(OaAiMessage::getRole, OaAiMessage.ROLE_ASSISTANT)
                .in(OaAiMessage::getConversationId, conversationIds)
                .orderByDesc(OaAiMessage::getId));
        List<QaHistoryVo> records = new ArrayList<>();
        for (OaAiMessage message : result.getRecords()) {
            QaHistoryVo vo = new QaHistoryVo();
            vo.setConversationId(message.getConversationId());
            vo.setMessageId(message.getId());
            vo.setQuestion(questionBefore(message));
            vo.setAnswer(message.getContent());
            vo.setCitations(parseCitations(message.getCitations()));
            vo.setCreateTime(message.getCreateTime());
            records.add(vo);
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    // ---------------------------------------------------------------- stream plumbing

    private QaHandle startStream(OaAiConversation conversation, OaAiMessage assistantMessage,
                                 List<ChatTransport.Turn> turns, List<AiKbSearchHitVo> hits,
                                 Long userId, String username, StreamListener listener) {
        LlmGateway.CompletionRequest request = new LlmGateway.CompletionRequest(
            userId, username, AiBizType.RAG.code(), conversation.getId(), null,
            conversation.getModelId(), null, null, turns);

        QaSession session = new QaSession();
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
                String content = result.text().isBlank() ? session.buffer.toString() : result.text();
                List<QaCitationVo> citations = toCitations(hits);
                assistantMessage.setContent(content);
                assistantMessage.setCitations(JsonUtils.toJsonString(citations));
                assistantMessage.setPromptTokens(result.promptTokens());
                assistantMessage.setCompletionTokens(result.completionTokens());
                assistantMessage.setStatus(OaAiMessage.STATUS_DONE);
                messageMapper.updateById(assistantMessage);
                conversation.setLastMessageTime(new java.util.Date());
                if (conversation.getTitle() == null || conversation.getTitle().isBlank()
                    || "新对话".equals(conversation.getTitle())) {
                    String title = hits.isEmpty() ? "知识问答" : hits.get(0).getTitle();
                    String question = questionBefore(assistantMessage);
                    conversation.setTitle(autoTitle(title, question));
                }
                conversationMapper.updateById(conversation);

                QaResultVo vo = new QaResultVo();
                vo.setConversationId(conversation.getId());
                vo.setMessageId(assistantMessage.getId());
                vo.setContent(content);
                vo.setPromptTokens(result.promptTokens());
                vo.setCompletionTokens(result.completionTokens());
                vo.setStatus(OaAiMessage.STATUS_DONE);
                vo.setCitations(citations);
                listener.onComplete(vo);
            }

            @Override
            public void onError(ServiceException error) {
                QaSession current = activeStreams.remove(assistantMessage.getId());
                assistantMessage.setContent(current == null ? "" : current.buffer.toString());
                assistantMessage.setErrorCode(errorCodeOf(error));
                assistantMessage.setStatus(current != null && current.ticket != null && current.ticket.isCancelled()
                    ? OaAiMessage.STATUS_STOPPED : OaAiMessage.STATUS_ERROR);
                messageMapper.updateById(assistantMessage);
                listener.onError(error);
            }
        });
        session.ticket = ticket;
        return new QaHandle() {
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

    // ---------------------------------------------------------------- internals

    /** 组装提示词：系统指令 + 参考资料（[n] 序号）+ 历史 + 问题 */
    static List<ChatTransport.Turn> buildTurns(List<AiKbSearchHitVo> hits, String query, List<OaAiMessage> history) {
        List<ChatTransport.Turn> turns = new ArrayList<>();
        turns.add(ChatTransport.Turn.system(SYSTEM_PROMPT));
        turns.add(ChatTransport.Turn.system(contextBlock(hits)));
        if (history != null) {
            for (OaAiMessage message : history) {
                if (OaAiMessage.ROLE_USER.equals(message.getRole())) {
                    turns.add(ChatTransport.Turn.user(message.getContent()));
                } else if (OaAiMessage.ROLE_ASSISTANT.equals(message.getRole())
                    && message.getContent() != null && !message.getContent().isBlank()) {
                    turns.add(ChatTransport.Turn.assistant(message.getContent()));
                }
            }
        }
        turns.add(ChatTransport.Turn.user(query));
        return turns;
    }

    private static String contextBlock(List<AiKbSearchHitVo> hits) {
        if (hits == null || hits.isEmpty()) {
            return "【参考资料】\n（未检索到相关资料）";
        }
        StringBuilder block = new StringBuilder("【参考资料】");
        for (int i = 0; i < hits.size(); i++) {
            AiKbSearchHitVo hit = hits.get(i);
            block.append('\n').append('[').append(i + 1).append("] ");
            if (hit.getTitle() != null && !hit.getTitle().isBlank()) {
                block.append('《').append(hit.getTitle()).append('》');
            }
            if (hit.getHeading() != null && !hit.getHeading().isBlank()) {
                block.append(" · ").append(hit.getHeading());
            }
            block.append('\n').append(hit.getSnippet() == null ? "" : hit.getSnippet());
        }
        return block.toString();
    }

    private static List<QaCitationVo> toCitations(List<AiKbSearchHitVo> hits) {
        List<QaCitationVo> citations = new ArrayList<>();
        if (hits == null) {
            return citations;
        }
        for (int i = 0; i < hits.size(); i++) {
            AiKbSearchHitVo hit = hits.get(i);
            QaCitationVo citation = new QaCitationVo();
            citation.setIndex(i + 1);
            citation.setKbId(hit.getKbId());
            citation.setSourceId(hit.getSourceId());
            citation.setSourceType(hit.getSourceType());
            citation.setDocId(hit.getDocId());
            citation.setFileId(hit.getFileId());
            citation.setTitle(hit.getTitle());
            citation.setHeading(hit.getHeading());
            citation.setSnippet(hit.getSnippet());
            citation.setScore(hit.getScore());
            citation.setLink(hit.getLink());
            citations.add(citation);
        }
        return citations;
    }

    private OaAiConversation newConversation(Long userId, AiQaRequestBo bo) {
        OaAiConversation conversation = new OaAiConversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setModelId(bo.getModelId());
        conversation.setStatus(OaAiConversation.STATUS_ACTIVE);
        conversation.setScene(OaAiConversation.SCENE_QA);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private OaAiConversation requireOwnedQaConversation(Long conversationId, Long userId) {
        OaAiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
            || !userId.equals(conversation.getUserId())
            || !OaAiConversation.STATUS_ACTIVE.equals(conversation.getStatus())
            || !OaAiConversation.SCENE_QA.equals(conversation.getScene())) {
            throw new ServiceException("AI_CONVERSATION_NOT_FOUND 会话不存在", 404);
        }
        return conversation;
    }

    private OaAiMessage insertMessage(OaAiConversation conversation, String role, String content,
                                      String citations, String status) {
        OaAiMessage message = new OaAiMessage();
        message.setConversationId(conversation.getId());
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCitations(citations);
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

    private String questionBefore(OaAiMessage assistantMessage) {
        OaAiMessage question = messageMapper.selectOne(new LambdaQueryWrapper<OaAiMessage>()
            .eq(OaAiMessage::getConversationId, assistantMessage.getConversationId())
            .eq(OaAiMessage::getRole, OaAiMessage.ROLE_USER)
            .lt(OaAiMessage::getId, assistantMessage.getId())
            .orderByDesc(OaAiMessage::getId)
            .last("LIMIT 1"));
        return question == null ? null : question.getContent();
    }

    private List<QaCitationVo> parseCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        List<QaCitationVo> citations = JsonUtils.parseArray(citationsJson, QaCitationVo.class);
        return citations == null ? List.of() : citations;
    }

    private String autoTitle(String firstTitle, String question) {
        String base = question == null || question.isBlank()
            ? (firstTitle == null ? "知识问答" : firstTitle)
            : question.replaceAll("\\s+", " ").strip();
        return base.length() > 24 ? base.substring(0, 24) : base;
    }

    private String errorCodeOf(ServiceException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "LLM_UPSTREAM_ERROR";
        }
        int space = message.indexOf(' ');
        return space > 0 ? message.substring(0, space) : message;
    }
}
