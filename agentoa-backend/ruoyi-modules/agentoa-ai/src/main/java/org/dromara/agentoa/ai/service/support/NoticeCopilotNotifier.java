package org.dromara.agentoa.ai.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.notice.domain.bo.TemplateSendBo;
import org.dromara.agentoa.notice.service.INoticeTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 业务助手任务完成通知实现（docs/21 AI-M4-06）。
 * <p>
 * 走通知模板 {@code ai_task_done} → outbox → WS/SSE 站内消息；任何失败只记日志，
 * 不影响任务结果（AI 失败不得导致业务回滚失败，见 docs/21 §8）。
 */
@Component
@RequiredArgsConstructor
public class NoticeCopilotNotifier implements CopilotNotifier {

    /** 内置通知模板编码（V32 预置） */
    public static final String TEMPLATE_CODE = "ai_task_done";

    /** 受众范围：指定人 */
    private static final String SCOPE_USERS = "4";

    private static final Logger log = LoggerFactory.getLogger(NoticeCopilotNotifier.class);

    private final INoticeTemplateService templateService;

    @Override
    public void taskDone(Long userId, String sceneName, Long taskId, boolean success) {
        if (userId == null) {
            return;
        }
        try {
            TemplateSendBo bo = new TemplateSendBo();
            bo.setScopeType(SCOPE_USERS);
            bo.setScopeValues(String.valueOf(userId));
            bo.setVars(Map.of(
                "scene", sceneName == null ? "业务助手" : sceneName,
                "taskId", taskId == null ? "-" : String.valueOf(taskId),
                "result", success ? "已完成" : "失败"));
            templateService.send(TEMPLATE_CODE, bo, userId);
        } catch (RuntimeException e) {
            log.warn("AI copilot task notification failed, taskId={}: {}", taskId, e.getMessage());
        }
    }
}
