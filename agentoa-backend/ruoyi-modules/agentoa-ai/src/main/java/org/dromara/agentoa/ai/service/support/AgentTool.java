package org.dromara.agentoa.ai.service.support;

/**
 * 内置函数工具（docs/21 AI-M5-01）：每个工具 = Java 实现 + JSON Schema + 权限声明。
 * <p>
 * 读类工具按调用者已有权限过滤；写类工具（{@link #writeTool()} 为 true）执行前需人工确认
 * 并持有 {@code ai:tool:write}，防止 Agent 误调造成损失（docs/21 §7.1 AI-M5-05）。
 */
public interface AgentTool {

    /** 工具码（与 {@code oa_ai_tool.code} 一致） */
    String code();

    String name();

    /** 给模型看的用途说明 */
    String description();

    /** 入参 JSON Schema */
    String parametersJsonSchema();

    /** 写类工具标记 */
    default boolean writeTool() {
        return false;
    }

    /**
     * 执行工具并返回文本结果（会回填给模型）。
     *
     * @param argumentsJson 入参 JSON
     * @param userId        调用者
     * @param username      调用者名（写日志用）
     */
    String execute(String argumentsJson, Long userId, String username);
}
