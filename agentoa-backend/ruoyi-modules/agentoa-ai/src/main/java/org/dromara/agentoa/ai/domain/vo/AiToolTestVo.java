package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

/**
 * 工具连通性测试结果（docs/21 §7.3 POST /tools/{id}/test）。
 */
@Data
public class AiToolTestVo {

    private Boolean ok;

    /** function/mcp */
    private String type;

    /** MCP 可发现的工具数（function 工具为实现是否就绪） */
    private Integer toolCount;

    private Long latencyMs;

    private String message;
}
