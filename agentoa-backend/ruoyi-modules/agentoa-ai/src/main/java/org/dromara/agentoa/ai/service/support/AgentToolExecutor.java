package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.domain.OaAiTool;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.mapper.OaAiToolMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工具执行器（docs/21 AI-M5-05）：统一执行入口，落实权限与写类人工确认。
 * <p>
 * 读类工具按调用者已有权限过滤；写类工具需同时满足：
 * 1) 调用方在本次运行中显式确认（approved）；2) 调用者持有 {@code ai:tool:write}。
 * 任一不满足即拒绝执行并在轨迹中记为 skipped，防止工具误调造成损失。
 */
@Component
public class AgentToolExecutor {

    /** 工具结果摘要上限（回填给模型） */
    private static final int MAX_RESULT_CHARS = 4000;

    private final OaAiToolMapper toolMapper;
    private final FunctionToolRegistry functionRegistry;
    private final McpToolGateway mcpGateway;

    public AgentToolExecutor(OaAiToolMapper toolMapper, FunctionToolRegistry functionRegistry,
                             McpToolGateway mcpGateway) {
        this.toolMapper = toolMapper;
        this.functionRegistry = functionRegistry;
        this.mcpGateway = mcpGateway;
    }

    /** 一次工具执行结果 */
    public record ToolOutcome(String tool, boolean skipped, boolean writeTool, String result, long durationMs) {
    }

    /**
     * 按工具码执行；未登记/停用/未授权一律返回可读错误文本，不抛给模型。
     *
     * @param approvedWriteTools 本次运行中人工逐项确认过的写类工具码
     */
    public ToolOutcome execute(String toolCode, String argumentsJson, Long userId, String username,
                               Set<String> approvedWriteTools, Set<String> permissions) {
        long start = System.currentTimeMillis();
        OaAiTool tool = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
            .eq(OaAiTool::getCode, toolCode)
            .last("LIMIT 1"));
        if (tool == null) {
            return new ToolOutcome(toolCode, true, false,
                "工具 " + toolCode + " 未登记", System.currentTimeMillis() - start);
        }
        if (tool.getEnabled() == null || tool.getEnabled() != 1) {
            return new ToolOutcome(toolCode, true, tool.getWriteFlag() != null && tool.getWriteFlag() == 1,
                "工具 " + toolCode + " 已停用", System.currentTimeMillis() - start);
        }
        boolean writeTool = tool.getWriteFlag() != null && tool.getWriteFlag() == 1;
        if (writeTool) {
            if (approvedWriteTools == null || !approvedWriteTools.contains(toolCode)) {
                return new ToolOutcome(toolCode, true, true,
                    "写类工具 " + toolCode + " 需人工确认后才能执行", System.currentTimeMillis() - start);
            }
            if (!hasWritePermission(permissions)) {
                return new ToolOutcome(toolCode, true, true,
                    "当前账号缺少 " + AiAccessPolicy.PERM_TOOL_WRITE + " 权限，无法执行写类工具",
                    System.currentTimeMillis() - start);
            }
        }
        try {
            String result;
            if (OaAiTool.TYPE_MCP.equals(tool.getType())) {
                result = mcpGateway.execute(tool.getConfigJson(), toolCode, argumentsJson);
            } else {
                AgentTool impl = functionRegistry.find(toolCode);
                if (impl == null) {
                    return new ToolOutcome(toolCode, true, writeTool,
                        "工具 " + toolCode + " 没有对应的 Java 实现", System.currentTimeMillis() - start);
                }
                result = impl.execute(argumentsJson, userId, username);
            }
            return new ToolOutcome(toolCode, false, writeTool, truncate(result), System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return new ToolOutcome(toolCode, false, writeTool,
                "工具执行失败：" + truncate(safeMessage(e)), System.currentTimeMillis() - start);
        }
    }

    /** 工具是否为写类（未登记返回 false） */
    public boolean isWriteTool(String toolCode) {
        if (toolCode == null) {
            return false;
        }
        OaAiTool tool = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
            .eq(OaAiTool::getCode, toolCode)
            .last("LIMIT 1"));
        return tool != null && tool.getWriteFlag() != null && tool.getWriteFlag() == 1;
    }

    private boolean hasWritePermission(Set<String> permissions) {
        return permissions != null && (permissions.contains("*:*:*")
            || permissions.contains(AiAccessPolicy.PERM_TOOL_WRITE));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_RESULT_CHARS ? text : text.substring(0, MAX_RESULT_CHARS) + "…";
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    /** 当前登录用户的权限集（非登录上下文返回空集） */
    public static Set<String> currentPermissions() {
        try {
            return LoginHelper.getLoginUser() == null ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        } catch (RuntimeException e) {
            return Set.of();
        }
    }
}
