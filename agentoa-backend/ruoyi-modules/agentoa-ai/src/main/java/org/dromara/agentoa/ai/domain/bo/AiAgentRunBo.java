package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 运行请求（docs/21 §7.3 POST /agents/{id}/run，SSE）。
 */
@Data
public class AiAgentRunBo {

    /** 关联对话 ID（可选，Agent 模式复用会话） */
    private Long conversationId;

    @NotBlank(message = "输入不能为空")
    @Size(max = 8000, message = "输入最长 {max} 字符")
    private String input;

    /** 人工确认后允许执行的写类工具调用（工具码列表） */
    @Size(max = 20, message = "确认项最多 {max} 个")
    private java.util.List<String> approvedWriteTools;
}
