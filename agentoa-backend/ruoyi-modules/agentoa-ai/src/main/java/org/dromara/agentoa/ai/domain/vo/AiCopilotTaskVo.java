package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 业务助手任务视图（docs/21 §6.3 GET /copilot/tasks）。
 */
@Data
public class AiCopilotTaskVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String sceneCode;

    private String sceneName;

    private String bizType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String status;

    private String errorMsg;

    private Integer promptTokens;

    private Integer totalTokens;

    private Date finishTime;

    private Date createTime;

    /** 生成结果（列表可不带正文时置空） */
    private String output;
}
