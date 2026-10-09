package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQaRequestBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.QaHistoryVo;
import org.dromara.agentoa.ai.domain.vo.QaResultVo;
import org.dromara.agentoa.ai.service.IAiQaService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识问答（docs/21 M3）：SSE 流式输出（event: meta/delta/usage/done/error），
 * done 事件携带引用列表（文档名 + 章节/片段 + 跳转链接）。
 * <p>
 * AI 生成内容仅供参考；问答按账号隔离，引用片段不越权泄露原文。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/qa")
public class AiQaController {

    /** 流式响应最长 10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final IAiQaService qaService;

    @SaCheckPermission(AiAccessPolicy.PERM_QA_USE)
    @Log(title = "AI 知识问答", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Validated @RequestBody AiQaRequestBo bo, HttpServletResponse response) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        IAiQaService.QaHandle handle =
            qaService.ask(bo, LoginHelper.getUserId(), LoginHelper.getUsername(), listener);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("X-Accel-Buffering", "no");
        listener.start(handle);
        return emitter;
    }

    @SaCheckPermission(AiAccessPolicy.PERM_QA_USE)
    @GetMapping("/history")
    public R<PageVo<QaHistoryVo>> history(AiPageQuery page) {
        return R.ok(qaService.history(page, LoginHelper.getUserId()));
    }

    /** SSE 事件适配（event: meta/delta/usage/done/error） */
    private static final class SseListener implements IAiQaService.StreamListener {

        private final SseEmitter emitter;
        private final List<Runnable> pending = new ArrayList<>();
        private boolean started;

        private SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        /** 先发 meta（会话/消息 ID），再放行后续事件 */
        synchronized void start(IAiQaService.QaHandle handle) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("conversationId", handle.conversationId());
            meta.put("messageId", handle.messageId());
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
        public synchronized void onComplete(QaResultVo result) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", result.getPromptTokens());
            usage.put("completionTokens", result.getCompletionTokens());
            usage.put("totalTokens", result.getPromptTokens() + result.getCompletionTokens());
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
                // 客户端断开：停止后续推送
            }
        }

        private String errorCodeOf(ServiceException error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) {
                return "AI_QA_FAILED";
            }
            int space = message.indexOf(' ');
            return space > 0 ? message.substring(0, space) : message;
        }
    }
}
