package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 用量日志视图。
 */
@Data
public class AiUsageLogVo {

    private Long id;

    private Long userId;

    private String username;

    private Long providerId;

    private String modelKey;

    private String bizType;

    private Long conversationId;

    private String taskId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer latencyMs;

    private String status;

    private String errorCode;

    private String errorMsg;

    private Date createTime;
}
