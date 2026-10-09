package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 业务助手场景视图（docs/21 §6.1/6.3 GET /copilot/scenes）。
 */
@Data
public class AiCopilotSceneVo {

    private String code;

    private String name;

    /** 场景说明（能力边界：只读摘要 + 草稿） */
    private String description;

    /** 1 启用 0 停用 */
    private Integer enabled;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 模型展示名（可空） */
    private String modelName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long promptTemplateId;

    private String promptTemplateName;

    /** 默认业务类型 */
    private String bizType;

    /** 当前用户是否可配置（管理员） */
    private Boolean configurable;
}
