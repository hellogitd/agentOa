package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * Agent 视图（docs/21 AI-M5-03）。
 */
@Data
public class AiAgentVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String code;

    private String name;

    private String systemPrompt;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    private String modelName;

    private List<String> toolCodes;

    private Integer maxSteps;

    private Integer timeoutSec;

    private Integer enabled;

    private Integer isBuiltin;

    private String remark;
}
