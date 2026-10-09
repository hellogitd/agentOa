package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiAgentBo;
import org.dromara.agentoa.ai.domain.bo.AiAgentRunBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiAgentRunVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentTraceStepVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentVo;
import org.dromara.agentoa.ai.service.IAiAgentService;
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
 * Agent 配置与运行（docs/21 M5）。
 * <p>
 * 运行为 SSE：event: meta/step/delta/usage/done/error；step 事件即执行轨迹，供 UI 观察。
 * AI 生成内容仅供参考；写类工具需人工确认后执行。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/agents")
public class AiAgentController {

    /** 流式响应最长 10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final IAiAgentService agentService;

    // ---------------------------------------------------------------- agents

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_QUERY)
    @GetMapping
    public R<PageVo<AiAgentVo>> list(AiPageQuery page) {
        return R.ok(agentService.page(page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_QUERY)
    @GetMapping("/{id}")
    public R<AiAgentVo> get(@PathVariable Long id) {
        return R.ok(agentService.get(id));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_ADD)
    @Log(title = "AI Agent", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @RepeatSubmit()
    @PostMapping
    public R<AiAgentVo> add(@Validated @RequestBody AiAgentBo bo) {
        return R.ok(agentService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_EDIT)
    @Log(title = "AI Agent", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiAgentVo> edit(@PathVariable Long id, @Validated @RequestBody AiAgentBo bo) {
        bo.setId(id);
        return R.ok(agentService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_REMOVE)
    @Log(title = "AI Agent", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        agentService.delete(id);
        return R.ok();
    }

    // ---------------------------------------------------------------- run（SSE）

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_RUN)
    @Log(title = "AI Agent", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping(value = "/{id}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable Long id, @Validated @RequestBody AiAgentRunBo bo, HttpServletResponse response) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        IAiAgentService.RunHandle handle =
            agentService.run(id, bo, LoginHelper.getUserId(), LoginHelper.getUsername(), listener);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("X-Accel-Buffering", "no");
        listener.start(handle);
        return emitter;
    }

    // ---------------------------------------------------------------- runs

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_QUERY)
    @GetMapping("/runs")
    public R<PageVo<AiAgentRunVo>> runs(@RequestParam(required = false) Long agentId, AiPageQuery page) {
        return R.ok(agentService.runs(agentId, page, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_AGENT_QUERY)
    @GetMapping("/runs/{runId}")
    public R<AiAgentRunVo> runDetail(@PathVariable Long runId) {
        return R.ok(agentService.runDetail(runId, LoginHelper.getUserId()));
    }

    // ---------------------------------------------------------------- SSE 事件

    /** SSE 事件适配（event: meta/step/delta/usage/done/error） */
    private static final class SseListener implements IAiAgentService.RunListener {

        private final SseEmitter emitter;
        private final List<Runnable> pending = new ArrayList<>();
        private boolean started;
        private int promptTokens;
        private int totalTokens;

        private SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        synchronized void start(IAiAgentService.RunHandle handle) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("runId", handle.runId());
            send("meta", meta);
            started = true;
            for (Runnable task : pending) {
                task.run();
            }
            pending.clear();
        }

        @Override
        public synchronized void onStep(AiAgentTraceStepVo step) {
            emit("step", step);
        }

        @Override
        public synchronized void onDelta(String delta) {
            emit("delta", Map.of("text", delta == null ? "" : delta));
        }

        @Override
        public synchronized void onComplete(AiAgentRunVo run) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", promptTokens);
            usage.put("completionTokens", run.getTotalTokens() == null ? 0 : run.getTotalTokens());
            usage.put("totalTokens", run.getTotalTokens() == null ? 0 : run.getTotalTokens());
            emit("usage", usage);
            emit("done", run);
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
                return "AI_AGENT_FAILED";
            }
            int space = message.indexOf(' ');
            return space > 0 ? message.substring(0, space) : message;
        }
    }
}
