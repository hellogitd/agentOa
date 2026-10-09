package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 工具 oa_ai_tool（docs/21 AI-M5-01/02）。
 * <p>
 * type=function 为内置 Java 工具；type=mcp 为外部 MCP server 连接。
 * 写类工具（write_flag=1）执行前需人工确认并持有 {@code ai:tool:write}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_tool")
public class OaAiTool extends TenantEntity {

    public static final String TYPE_FUNCTION = "function";
    public static final String TYPE_MCP = "mcp";

    @TableId(value = "id")
    private Long id;

    /** 工具码（Agent 引用名） */
    private String code;

    private String name;

    /** function/mcp */
    private String type;

    /** 给模型看的用途说明 */
    private String description;

    /** 参数 JSON Schema */
    private String schemaJson;

    /** MCP 连接配置 JSON（url/stdio/command/headers） */
    private String configJson;

    /** 1 写类工具（需人工确认） 0 读类 */
    private Integer writeFlag;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 1 内置 0 自定义 */
    private Integer isBuiltin;

    private String remark;
}
