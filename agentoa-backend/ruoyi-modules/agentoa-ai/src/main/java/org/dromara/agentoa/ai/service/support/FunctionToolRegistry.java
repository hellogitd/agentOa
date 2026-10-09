package org.dromara.agentoa.ai.service.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置函数工具注册表（docs/21 AI-M5-01）。
 * <p>
 * 自动收集 Spring 上的 {@link AgentTool} Bean；工具码重复时以首个为准并告警。
 */
@Component
public class FunctionToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public FunctionToolRegistry(List<AgentTool> beans) {
        if (beans != null) {
            for (AgentTool tool : beans) {
                tools.putIfAbsent(tool.code(), tool);
            }
        }
    }

    public boolean supports(String code) {
        return code != null && tools.containsKey(code);
    }

    /** 注册/覆盖一个函数工具（测试与自定义扩展用） */
    public void register(AgentTool tool) {
        if (tool != null && tool.code() != null) {
            tools.put(tool.code(), tool);
        }
    }

    public AgentTool find(String code) {
        return code == null ? null : tools.get(code);
    }

    public List<AgentTool> all() {
        return List.copyOf(tools.values());
    }
}
