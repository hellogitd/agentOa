package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 工具视图（docs/21 AI-M5-01/02）。
 * <p>
 * MCP 配置中的鉴权头不回显，仅返回是否已配置的标记。
 */
@Data
public class AiToolVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String code;

    private String name;

    /** function/mcp */
    private String type;

    private String description;

    private String schemaJson;

    /** MCP 连接地址（鉴权头不回显） */
    private String endpoint;

    /** 是否已配置鉴权头 */
    private Boolean hasAuthHeader;

    /** 1 写类 0 读类 */
    private Integer writeFlag;

    private Integer enabled;

    private Integer isBuiltin;

    private String remark;
}
