package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiChatRequestBo;
import org.dromara.agentoa.ai.domain.bo.AiConversationBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiChatResultVo;
import org.dromara.agentoa.ai.domain.vo.AiConversationVo;
import org.dromara.agentoa.ai.domain.vo.AiMessageVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;

/**
 * AI 对话服务（docs/21 M2）：会话/消息隔离、流式补全、停止与重生成。
 */
public interface IAiChatService {

    /** 流式回调（错误统一为 ServiceException） */
    interface StreamListener {
        void onDelta(String delta);

        void onComplete(AiChatResultVo result);

        void onError(ServiceException error);
    }

    /** 流式句柄 */
    interface ChatHandle {
        void cancel();

        Long conversationId();

        Long messageId();
    }

    PageVo<AiConversationVo> conversations(AiPageQuery page, Long userId);

    AiConversationVo conversation(Long id, Long userId);

    AiConversationVo createConversation(AiConversationBo bo, Long userId);

    AiConversationVo updateConversation(Long id, AiConversationBo bo, Long userId);

    void deleteConversation(Long id, Long userId);

    PageVo<AiMessageVo> messages(Long conversationId, AiPageQuery page, Long userId);

    /** 发起补全（SSE 由控制器适配）；conversationId 为空时自动建会话 */
    ChatHandle chat(AiChatRequestBo bo, Long userId, String username, StreamListener listener);

    /** 停止生成：取消上游输出，消息置为 stopped */
    void stop(Long messageId, Long userId);

    /** 重新生成：保留旧版本，产生新 assistant 消息 */
    ChatHandle regenerate(Long messageId, Long userId, String username, StreamListener listener);
}
