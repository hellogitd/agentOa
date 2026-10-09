package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiChatRequestBo;
import org.dromara.agentoa.ai.domain.bo.AiConversationBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiChatResultVo;
import org.dromara.agentoa.ai.domain.vo.AiConversationVo;
import org.dromara.agentoa.ai.domain.vo.AiMessageVo;
import org.dromara.agentoa.ai.service.IAiChatService;
import org.dromara.agentoa.ai.service.support.AiImageStore;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 对话（docs/21 M2）：SSE 流式输出（event: delta/done/usage/error），绕过 envelope。
 * <p>
 * AI 生成内容仅供参考；图片附件走私有文件存储，会话与消息按账号隔离。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {

    /** 流式响应最长 10 分钟 */
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final IAiChatService chatService;
    private final AiImageStore imageStore;

    // ---------------------------------------------------------------- conversations

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @GetMapping("/conversations")
    public R<PageVo<AiConversationVo>> conversations(AiPageQuery page) {
        return R.ok(chatService.conversations(page, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @RepeatSubmit()
    @PostMapping("/conversations")
    public R<AiConversationVo> createConversation(@RequestBody(required = false) AiConversationBo bo) {
        return R.ok(chatService.createConversation(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @GetMapping("/conversations/{id}")
    public R<AiConversationVo> conversation(@PathVariable Long id) {
        return R.ok(chatService.conversation(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @RepeatSubmit()
    @PutMapping("/conversations/{id}")
    public R<AiConversationVo> updateConversation(@PathVariable Long id, @RequestBody AiConversationBo bo) {
        return R.ok(chatService.updateConversation(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @DeleteMapping("/conversations/{id}")
    public R<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @GetMapping("/conversations/{id}/messages")
    public R<PageVo<AiMessageVo>> messages(@PathVariable Long id, AiPageQuery page) {
        return R.ok(chatService.messages(id, page, LoginHelper.getUserId()));
    }

    // ---------------------------------------------------------------- completions（SSE）

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @Log(title = "AI 对话", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completions(@Validated @RequestBody AiChatRequestBo bo, HttpServletResponse response) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        IAiChatService.ChatHandle handle =
            chatService.chat(bo, LoginHelper.getUserId(), LoginHelper.getUsername(), listener);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("X-Accel-Buffering", "no");
        listener.start(handle);
        return emitter;
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @PostMapping("/messages/{id}/stop")
    public R<Void> stop(@PathVariable Long id) {
        chatService.stop(id, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @Log(title = "AI 对话", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping(value = "/messages/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id, HttpServletResponse response) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseListener listener = new SseListener(emitter);
        IAiChatService.ChatHandle handle =
            chatService.regenerate(id, LoginHelper.getUserId(), LoginHelper.getUsername(), listener);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("X-Accel-Buffering", "no");
        listener.start(handle);
        return emitter;
    }

    // ---------------------------------------------------------------- attachments

    @SaCheckPermission(AiAccessPolicy.PERM_CHAT_USE)
    @Log(title = "AI 对话附件", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) throws Exception {
        AiImageStore.StoredImage stored = imageStore.upload(file.getOriginalFilename(), file.getBytes(),
            LoginHelper.getUserId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", Long.toString(stored.fileId()));
        payload.put("fileName", stored.fileName());
        payload.put("contentType", stored.contentType());
        payload.put("sizeBytes", stored.sizeBytes());
        return R.ok(payload);
    }

    // ---------------------------------------------------------------- SSE 事件

    /** SSE 事件适配（event: meta/delta/done/usage/error） */
    private static final class SseListener implements IAiChatService.StreamListener {

        private final SseEmitter emitter;
        private final java.util.List<Runnable> pending = new java.util.ArrayList<>();
        private boolean started;

        private SseListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        /** 先发 meta（会话/消息 ID），再放行后续事件 */
        synchronized void start(IAiChatService.ChatHandle handle) {
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
        public synchronized void onComplete(AiChatResultVo result) {
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
                return "AI_CHAT_FAILED";
            }
            int space = message.indexOf(' ');
            return space > 0 ? message.substring(0, space) : message;
        }
    }
}
