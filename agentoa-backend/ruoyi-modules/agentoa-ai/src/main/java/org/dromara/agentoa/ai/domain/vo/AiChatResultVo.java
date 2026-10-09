package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

/**
 * 对话补全完成载荷（SSE done 事件 data）。
 */
@Data
public class AiChatResultVo {

    private Long conversationId;

    private Long messageId;

    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private String status;
}
