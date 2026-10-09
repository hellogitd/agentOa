package org.dromara.agentoa.ai.service.support;

/**
 * 业务助手任务完成通知（docs/21 AI-M4-06：生成结果通过 outbox/WS 通知）。
 * <p>
 * 通知失败不得影响任务本身；实现可选，缺失时静默降级。
 */
public interface CopilotNotifier {

    /** 任务完成/失败后通知发起人 */
    void taskDone(Long userId, String sceneName, Long taskId, boolean success);
}
