package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 用量日志 oa_ai_usage_log（docs/21 AI-M1-07）：异步落库，审计与限额依据。
 * <p>
 * 日志表只追加，不携带完整审计列（同 docs/21 §3.2 列清单）。
 */
@Data
@TableName("oa_ai_usage_log")
public class OaAiUsageLog {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_STOPPED = "stopped";

    @TableId(value = "id")
    private Long id;

    private String tenantId;

    private Long userId;

    private String username;

    private Long providerId;

    private String modelKey;

    /** chat/rag/copilot/agent/probe */
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
