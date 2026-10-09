package org.dromara.agentoa.ai.domain.policy;

/**
 * AI 模块权限矩阵（docs/21 §2.4，权限串前缀 ai:）。
 * <p>
 * 纯常量与纯函数，不在运行时读取 Spring 上下文。
 */
public final class AiAccessPolicy {

    public static final String PERM_CHAT_USE = "ai:chat:use";

    public static final String PERM_PROVIDER_QUERY = "ai:provider:query";
    public static final String PERM_PROVIDER_ADD = "ai:provider:add";
    public static final String PERM_PROVIDER_EDIT = "ai:provider:edit";
    public static final String PERM_PROVIDER_REMOVE = "ai:provider:remove";
    public static final String PERM_PROVIDER_TEST = "ai:provider:test";

    public static final String PERM_MODEL_QUERY = "ai:model:query";
    public static final String PERM_MODEL_ADD = "ai:model:add";
    public static final String PERM_MODEL_EDIT = "ai:model:edit";
    public static final String PERM_MODEL_REMOVE = "ai:model:remove";

    public static final String PERM_PROMPT_QUERY = "ai:prompt:query";
    public static final String PERM_PROMPT_ADD = "ai:prompt:add";
    public static final String PERM_PROMPT_EDIT = "ai:prompt:edit";
    public static final String PERM_PROMPT_REMOVE = "ai:prompt:remove";

    public static final String PERM_USAGE_LIST = "ai:usage:list";
    public static final String PERM_USAGE_EXPORT = "ai:usage:export";
    public static final String PERM_QUOTA_LIST = "ai:quota:list";
    public static final String PERM_QUOTA_EDIT = "ai:quota:edit";

    public static final String PERM_QA_USE = "ai:qa:use";

    public static final String PERM_KB_QUERY = "ai:kb:query";
    public static final String PERM_KB_ADD = "ai:kb:add";
    public static final String PERM_KB_EDIT = "ai:kb:edit";
    public static final String PERM_KB_REMOVE = "ai:kb:remove";
    public static final String PERM_KB_INDEX = "ai:kb:index";

    /** 业务助手（docs/21 §6） */
    public static final String PERM_COPILOT_USE = "ai:copilot:use";
    public static final String PERM_COPILOT_CONFIG = "ai:copilot:config";

    /** 工具与 MCP（docs/21 §7） */
    public static final String PERM_TOOL_QUERY = "ai:tool:query";
    public static final String PERM_TOOL_ADD = "ai:tool:add";
    public static final String PERM_TOOL_EDIT = "ai:tool:edit";
    public static final String PERM_TOOL_REMOVE = "ai:tool:remove";
    public static final String PERM_TOOL_TEST = "ai:tool:test";
    /** 写类工具执行（需人工确认后执行） */
    public static final String PERM_TOOL_WRITE = "ai:tool:write";

    /** Agent（docs/21 §7） */
    public static final String PERM_AGENT_QUERY = "ai:agent:query";
    public static final String PERM_AGENT_ADD = "ai:agent:add";
    public static final String PERM_AGENT_EDIT = "ai:agent:edit";
    public static final String PERM_AGENT_REMOVE = "ai:agent:remove";
    public static final String PERM_AGENT_RUN = "ai:agent:run";

    private AiAccessPolicy() {
    }

    /** 会话是否归属本人（对象权限：他人会话一律按不存在处理） */
    public static boolean ownsConversation(Long ownerUserId, Long currentUserId) {
        return ownerUserId != null && ownerUserId.equals(currentUserId);
    }
}
