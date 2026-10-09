package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Agent 运行记录视图（docs/21 §7.3 GET /agents/runs、GET /agents/runs/{id}）。
 */
@Data
public class AiAgentRunVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    private String agentName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    private String input;

    private String output;

    private String status;

    private String errorMsg;

    private Integer totalTokens;

    private Integer durationMs;

    /** 执行轨迹（详情接口返回） */
    private List<AiAgentTraceStepVo> trace;

    private Date createTime;
}
