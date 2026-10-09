package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务助手场景配置更新（docs/21 §6.3 PUT /copilot/scenes/{code}）。
 */
@Data
public class AiCopilotSceneBo {

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 生成模型 ID（空表示用全局默认） */
    private Long modelId;

    /** 提示词模板 ID */
    private Long promptTemplateId;

    @Size(max = 500, message = "备注最长 {max} 字符")
    private String remark;
}
