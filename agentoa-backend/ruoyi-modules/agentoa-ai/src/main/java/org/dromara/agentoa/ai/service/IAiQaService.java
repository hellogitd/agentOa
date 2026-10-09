package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQaRequestBo;
import org.dromara.agentoa.ai.domain.vo.QaHistoryVo;
import org.dromara.agentoa.ai.domain.vo.QaResultVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;

/**
 * 知识问答服务（docs/21 M3）：混合检索 + 流式回答 + 引用溯源。
 */
public interface IAiQaService {

    /** 流式回调（错误统一为 ServiceException） */
    interface StreamListener {
        void onDelta(String delta);

        void onComplete(QaResultVo result);

        void onError(ServiceException error);
    }

    /** 流式句柄 */
    interface QaHandle {
        void cancel();

        Long conversationId();

        Long messageId();
    }

    /** 发起问答（SSE 由控制器适配）；conversationId 为空时自动建 qa 会话 */
    QaHandle ask(AiQaRequestBo bo, Long userId, String username, StreamListener listener);

    /** 问答历史（本人，一问一答 + 引用） */
    PageVo<QaHistoryVo> history(AiPageQuery page, Long userId);
}
