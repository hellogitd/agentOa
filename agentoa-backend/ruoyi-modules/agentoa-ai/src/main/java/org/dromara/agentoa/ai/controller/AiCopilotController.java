package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiCopilotRunBo;
import org.dromara.agentoa.ai.domain.bo.AiCopilotSceneBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiCopilotResultVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotSceneVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotTaskVo;
import org.dromara.agentoa.ai.service.IAiCopilotService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务助手（docs/21 M4）：场景清单/配置、SSE 生成、异步任务。
 * <p>
 * AI 生成内容仅供参考，人工确认后才进入业务流程；业务数据只取只读摘要。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/copilot")
public class AiCopilotController {

    /** 流式响应最长 10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final IAiCopilotService copilotService;

    // ---------------------------------------------------------------- scenes

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @GetMapping("/scenes")
    public R<List<AiCopilotSceneVo>> scenes() {
        return R.ok(copilotService.scenes(LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_CONFIG)
    @Log(title = "AI 业务助手", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/scenes/{code}")
    public R<AiCopilotSceneVo> updateScene(@PathVariable String code, @Validated @RequestBody AiCopilotSceneBo bo) {
        return R.ok(copilotService.updateScene(code, bo, LoginHelper.getUserId()));
    }

    // ---------------------------------------------------------------- 生成（SSE）

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @Log(title = "AI 业务助手", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping(value = "/{scene}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String scene, @Validated @RequestBody AiCopilotRunBo bo, HttpServletResponse response) {
        bo.setScene(scene);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        IAiCopilotService.RunHandle handle =
            copilotService.run(bo, LoginHelper.getUserId(), LoginHelper.getUsername(), listener);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("X-Accel-Buffering", "no");
        listener.start(handle);
        return emitter;
    }

    // ---------------------------------------------------------------- tasks

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @GetMapping("/tasks")
    public R<PageVo<AiCopilotTaskVo>> tasks(AiPageQuery page) {
        return R.ok(copilotService.tasks(page, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @GetMapping("/tasks/{id}")
    public R<AiCopilotTaskVo> task(@PathVariable Long id) {
        return R.ok(copilotService.task(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @Log(title = "AI 业务助手", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @RepeatSubmit()
    @PostMapping("/tasks/{id}/retry")
    public R<AiCopilotTaskVo> retry(@PathVariable Long id) {
        return R.ok(copilotService.retry(id, LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_COPILOT_USE)
    @Log(title = "AI 业务助手", businessType = BusinessType.DELETE, isSaveRequestData = false)
    @DeleteMapping("/tasks/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        copilotService.deleteTask(id, LoginHelper.getUserId());
        return R.ok();
    }

    // ---------------------------------------------------------------- SSE 事件

    /** SSE 事件适配（event: meta/delta/usage/done/error） */
    private static final class SseListener implements IAiCopilotService.StreamListener {

        private final SseEmitter emitter;
        private final List<Runnable> pending = new ArrayList<>();
        private boolean started;

        private SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        synchronized void start(IAiCopilotService.RunHandle handle) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskId", handle.taskId());
            send("meta", meta);
            started = true;
            for (Runnable task : pending) {
                task.run();
            }
            pending.clear();
        }

        @Override
        public synchronized void onDelta(String delta) {
            emit("delta", Map.of("text", delta == null ? "" : delta));
        }

        @Override
        public synchronized void onComplete(AiCopilotResultVo result) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", result.getPromptTokens());
            usage.put("completionTokens", result.getCompletionTokens());
            usage.put("totalTokens", result.getTotalTokens());
            emit("usage", usage);
            emit("done", result);
            emitter.complete();
        }

        @Override
        public synchronized void onError(ServiceException error) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("code", errorCodeOf(error));
            payload.put("msg", error.getMessage());
            emit("error", payload);
            emitter.complete();
        }

        private void emit(String event, Object data) {
            if (!started) {
                pending.add(() -> send(event, data));
                return;
            }
            send(event, data);
        }

        private void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ignored) {
                // 客户端断开即停止，不中断任务
            }
        }

        private String errorCodeOf(ServiceException error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) {
                return "AI_COPILOT_FAILED";
            }
            int space = message.indexOf(' ');
            return space > 0 ? message.substring(0, space) : message;
        }
    }
}
