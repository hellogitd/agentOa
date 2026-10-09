package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Agent 配置（docs/21 AI-M5-03）。
 */
@Data
public class AiAgentBo {

    private Long id;

    @NotBlank(message = "Agent 码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "Agent 码只能是小写字母、数字、下划线")
    private String code;

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 128, message = "Agent 名称最长 {max} 字符")
    private String name;

    @NotBlank(message = "系统提示词不能为空")
    @Size(max = 8000, message = "系统提示词最长 {max} 字符")
    private String systemPrompt;

    /** 默认模型 ID（空取全局默认） */
    private Long modelId;

    /** 可用工具码 */
    @Size(max = 40, message = "工具数最多 {max} 个")
    private List<String> toolCodes;

    @Min(value = 1, message = "最大步数至少 1")
    @Max(value = 20, message = "最大步数最多 20")
    private Integer maxSteps = 6;

    @Min(value = 5, message = "超时至少 5 秒")
    @Max(value = 600, message = "超时最多 600 秒")
    private Integer timeoutSec = 60;

    private Integer enabled = 1;

    @Size(max = 500, message = "备注最长 {max} 字符")
    private String remark;
}
