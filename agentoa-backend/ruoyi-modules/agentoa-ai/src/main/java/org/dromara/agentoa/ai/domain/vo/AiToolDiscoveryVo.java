package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

/**
 * MCP 工具发现结果（docs/21 §7.3 GET /tools/discover/{mcpServerId}）。
 */
@Data
public class AiToolDiscoveryVo {

    private String name;

    private String description;

    /** 入参 JSON Schema */
    private String parametersJsonSchema;
}
