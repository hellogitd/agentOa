package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工具登记（docs/21 AI-M5-01/02）。
 */
@Data
public class AiToolBo {

    private Long id;

    @NotBlank(message = "工具码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "工具码只能是小写字母、数字、下划线")
    private String code;

    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称最长 {max} 字符")
    private String name;

    @NotBlank(message = "工具类型不能为空")
    @Pattern(regexp = "function|mcp", message = "工具类型不在字典范围内")
    private String type = "function";

    @Size(max = 500, message = "用途说明最长 {max} 字符")
    private String description;

    /** 参数 JSON Schema（function 工具） */
    @Size(max = 4000, message = "参数 Schema 最长 {max} 字符")
    private String schemaJson;

    /** MCP 连接配置 JSON（mcp 工具） */
    @Size(max = 4000, message = "MCP 配置最长 {max} 字符")
    private String configJson;

    /** 1 写类工具（需人工确认 + ai:tool:write） */
    private Integer writeFlag = 0;

    private Integer enabled = 1;

    @Size(max = 500, message = "备注最长 {max} 字符")
    private String remark;
}
