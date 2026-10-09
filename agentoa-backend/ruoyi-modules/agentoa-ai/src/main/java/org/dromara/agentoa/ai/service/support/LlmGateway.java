package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.domain.OaAiUsageLog;
import org.dromara.agentoa.ai.domain.enums.AiBizType;
import org.dromara.agentoa.ai.domain.enums.AiCapability;
import org.dromara.agentoa.ai.domain.enums.AiProviderError;
import org.dromara.agentoa.ai.domain.vo.AiProviderTestVo;
import org.dromara.agentoa.ai.service.support.AiModelRouter.Route;
import org.dromara.agentoa.ai.service.support.ChatTransport.Call;
import org.dromara.agentoa.ai.service.support.ChatTransport.Result;
import org.dromara.agentoa.ai.service.support.ChatTransport.Turn;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一模型网关（docs/21 §2.2 / AI-M1-05/06/07/08）：限额、日志、重试、错误归一化。
 * <p>
 * 所有模型调用必须走本入口；禁止业务代码直连 HTTP。调用前置错误（路由/限额/能力）
 * 以同步异常抛出；流式过程中的错误经 {@link StreamListener#onError} 回调。
 */
@Component
public class LlmGateway {

    /** 流式空闲超时（docs/21 AI-M1-06） */
    public static final long STREAM_IDLE_TIMEOUT_MS = 60_000L;

    /** 流式中途配额复检步长（token）：累计产出每增加该值复检一次限额 */
    static final long QUOTA_CHECK_STEP_TOKENS = 256L;

    /** 流式中途配额复检步长（delta 数）：短片段流式按 delta 数兜底复检 */
    static final int QUOTA_CHECK_STEP_DELTAS = 16;

    private final AiModelRouter router;
    private final ChatTransport transport;
    private final EmbeddingTransport embeddingTransport;
    private final AiQuotaGuard quotaGuard;
    private final AiUsageRecorder usageRecorder;
    private final ProviderRegistry registry;
    private final ProviderHealth health;
    private final ScheduledExecutorService watchdog;

    @Autowired
    public LlmGateway(AiModelRouter router, ChatTransport transport, EmbeddingTransport embeddingTransport,
                      AiQuotaGuard quotaGuard, AiUsageRecorder usageRecorder, ProviderRegistry registry,
                      ProviderHealth health) {
        this.router = router;
        this.transport = transport;
        this.embeddingTransport = embeddingTransport;
        this.quotaGuard = quotaGuard;
        this.usageRecorder = usageRecorder;
        this.registry = registry;
        this.health = health;
        this.watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-stream-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 调用请求 */
    public record CompletionRequest(Long userId,
                                    String username,
                                    String bizType,
                                    Long conversationId,
                                    String taskId,
                                    Long modelId,
                                    Double temperature,
                                    Integer maxTokens,
                                    List<Turn> turns,
                                    List<ChatTransport.ToolSpec> tools) {

        /** 纯对话调用（不带工具声明） */
        public CompletionRequest(Long userId, String username, String bizType, Long conversationId, String taskId,
                                 Long modelId, Double temperature, Integer maxTokens, List<Turn> turns) {
            this(userId, username, bizType, conversationId, taskId, modelId, temperature, maxTokens, turns, null);
        }
    }

    /** 调用结果 */
    public record CompletionResult(String text,
                                   int promptTokens,
                                   int completionTokens,
                                   int totalTokens,
                                   Long providerId,
                                   String modelKey,
                                   long latencyMs,
                                   List<ChatTransport.ToolCall> toolCalls) {

        /** 纯文本结果（无工具调用） */
        public CompletionResult(String text, int promptTokens, int completionTokens, int totalTokens,
                                Long providerId, String modelKey, long latencyMs) {
            this(text, promptTokens, completionTokens, totalTokens, providerId, modelKey, latencyMs, null);
        }
    }

    /** 流式回调 */
    public interface StreamListener {
        void onDelta(String delta);

        void onComplete(CompletionResult result);

        void onError(ServiceException error);
    }

    /** 流式句柄 */
    public static final class StreamTicket {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ChatTransport.Handle handle;

        public void cancel() {
            cancelled.set(true);
            abort();
        }

        /**
         * 中断上游输出但不标记为用户取消（限额中途熔断等系统中断场景）：
         * 消息状态保持 error 语义，与用户「停止生成」（stopped）区分。
         */
        void abort() {
            ChatTransport.Handle current = handle;
            if (current != null) {
                current.cancel();
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        void attach(ChatTransport.Handle handle) {
            this.handle = handle;
            if (cancelled.get()) {
                handle.cancel();
            }
        }
    }

    // ---------------------------------------------------------------- sync

    public CompletionResult chat(CompletionRequest request) {
        long start = System.currentTimeMillis();
        List<Route> routes = filterByCapability(router.candidates(request.modelId()), request.turns());
        quotaGuard.checkBeforeCall(request.userId(),
            TokenEstimator.estimate(request.turns()) + reserveTokens(request.maxTokens()));
        LlmCallException last = null;
        for (Route route : routes) {
            try {
                Result result = transport.complete(callOf(route, request));
                health.markSuccess(route.provider().getId());
                CompletionResult completionResult = new CompletionResult(
                    result.text() == null ? "" : result.text(),
                    result.promptTokens() == null ? 0 : result.promptTokens(),
                    result.completionTokens() == null ? 0 : result.completionTokens(),
                    result.totalTokens() == null ? 0 : result.totalTokens(),
                    route.provider().getId(), route.modelKey(), System.currentTimeMillis() - start,
                    result.toolCalls() == null ? List.of() : result.toolCalls());
                recordUsage(request, route, completionResult, OaAiUsageLog.STATUS_SUCCESS, null, null);
                return completionResult;
            } catch (LlmCallException e) {
                last = e;
                health.markFailure(route.provider().getId());
                recordUsage(request, route, null, OaAiUsageLog.STATUS_FAILED, e.errorCode(), safeMessage(e));
            }
        }
        throw upstreamFailure(last);
    }

    // ---------------------------------------------------------------- embedding（AI-M3-03）

    /** 向量化请求 */
    public record EmbeddingRequest(Long userId,
                                   String username,
                                   String bizType,
                                   String taskId,
                                   Long modelId,
                                   List<String> texts) {
    }

    /** 向量化结果 */
    public record EmbeddingResult(List<float[]> vectors,
                                  int promptTokens,
                                  Long providerId,
                                  String modelKey,
                                  long latencyMs) {
    }

    /**
     * 批量向量化：仅允许 capability 含 embedding 的模型；限额、日志、错误归一化同补全。
     */
    public EmbeddingResult embed(EmbeddingRequest request) {
        long start = System.currentTimeMillis();
        List<Route> routes = filterByCapabilityCode(router.candidates(request.modelId()), AiCapability.EMBEDDING.code(),
            "当前模型不支持向量化");
        quotaGuard.checkBeforeCall(request.userId(), estimateTexts(request.texts()));
        LlmCallException last = null;
        for (Route route : routes) {
            try {
                EmbeddingTransport.Result result = embeddingTransport.embed(new EmbeddingTransport.Call(
                    route.provider().getBaseUrl(), route.apiKey(), route.modelKey(), request.texts()));
                health.markSuccess(route.provider().getId());
                int promptTokens = result.promptTokens() == null ? estimateTexts(request.texts()) : result.promptTokens();
                EmbeddingResult embeddingResult = new EmbeddingResult(
                    result.vectors() == null ? List.of() : result.vectors(), promptTokens,
                    route.provider().getId(), route.modelKey(), System.currentTimeMillis() - start);
                recordUsage(request, route, promptTokens, OaAiUsageLog.STATUS_SUCCESS, null, null);
                return embeddingResult;
            } catch (LlmCallException e) {
                last = e;
                health.markFailure(route.provider().getId());
                recordUsage(request, route, 0, OaAiUsageLog.STATUS_FAILED, e.errorCode(), safeMessage(e));
            }
        }
        throw upstreamFailure(last);
    }

    // ---------------------------------------------------------------- streaming

    public StreamTicket stream(CompletionRequest request, StreamListener listener) {
        long start = System.currentTimeMillis();
        List<Route> routes = filterByCapability(router.candidates(request.modelId()), request.turns());
        int inputTokens = TokenEstimator.estimate(request.turns());
        quotaGuard.checkBeforeCall(request.userId(), inputTokens + reserveTokens(request.maxTokens()));
        StreamTicket ticket = new StreamTicket();
        streamWithFallback(request, listener, ticket, routes, 0, start, inputTokens, new StringBuilder());
        return ticket;
    }

    private void streamWithFallback(CompletionRequest request, StreamListener listener, StreamTicket ticket,
                                    List<Route> routes, int index, long start, int inputTokens, StringBuilder buffer) {
        if (index >= routes.size()) {
            listener.onError(new ServiceException("LLM_UPSTREAM_ERROR 上游模型调用失败", 502));
            return;
        }
        Route route = routes.get(index);
        AtomicBoolean firstDelta = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
        AtomicLong accumulated = new AtomicLong();
        AtomicLong lastQuotaCheck = new AtomicLong();
        AtomicInteger deltaCount = new AtomicInteger();
        ScheduledFuture<?> idleTask = watchdog.scheduleAtFixedRate(() -> {
            if (done.get() || ticket.isCancelled()) {
                return;
            }
            if (System.currentTimeMillis() - lastActivity.get() > STREAM_IDLE_TIMEOUT_MS) {
                done.set(true);
                ticket.cancel();
                listener.onError(new ServiceException("LLM_TIMEOUT 流式输出空闲超时", 504));
            }
        }, 15L, 15L, TimeUnit.SECONDS);

        ChatTransport.Handle handle = transport.stream(callOf(route, request), new ChatTransport.Listener() {
            @Override
            public void onDelta(String delta) {
                if (ticket.isCancelled() || done.get()) {
                    return;
                }
                firstDelta.set(true);
                lastActivity.set(System.currentTimeMillis());
                buffer.append(delta);
                long consumed = accumulated.addAndGet(TokenEstimator.estimate(delta));
                int count = deltaCount.incrementAndGet();
                listener.onDelta(delta);
                if (consumed - lastQuotaCheck.get() >= QUOTA_CHECK_STEP_TOKENS || count % QUOTA_CHECK_STEP_DELTAS == 0) {
                    lastQuotaCheck.set(consumed);
                    try {
                        quotaGuard.checkMidStream(request.userId(), inputTokens + consumed);
                    } catch (ServiceException quotaError) {
                        if (done.getAndSet(true)) {
                            return;
                        }
                        idleTask.cancel(false);
                        ticket.abort();
                        recordUsage(request, route,
                            new CompletionResult(buffer.toString(), inputTokens, (int) consumed,
                                inputTokens + (int) consumed, route.provider().getId(), route.modelKey(),
                                System.currentTimeMillis() - start),
                            OaAiUsageLog.STATUS_FAILED, quotaErrorCode(quotaError), safeMessage(quotaError));
                        listener.onError(new ServiceException(quotaError.getMessage() + "（流式生成已中断）", 429));
                    }
                }
            }

            @Override
            public void onComplete(Result result) {
                if (done.getAndSet(true)) {
                    return;
                }
                idleTask.cancel(false);
                if (ticket.isCancelled()) {
                    return;
                }
                health.markSuccess(route.provider().getId());
                CompletionResult completionResult = new CompletionResult(
                    result.text() == null || result.text().isBlank() ? buffer.toString() : result.text(),
                    result.promptTokens() == null ? 0 : result.promptTokens(),
                    result.completionTokens() == null ? 0 : result.completionTokens(),
                    result.totalTokens() == null ? 0 : result.totalTokens(),
                    route.provider().getId(), route.modelKey(), System.currentTimeMillis() - start,
                    result.toolCalls() == null ? List.of() : result.toolCalls());
                recordUsage(request, route, completionResult, OaAiUsageLog.STATUS_SUCCESS, null, null);
                listener.onComplete(completionResult);
            }

            @Override
            public void onError(Throwable error) {
                if (done.getAndSet(true)) {
                    return;
                }
                idleTask.cancel(false);
                if (ticket.isCancelled()) {
                    return;
                }
                LlmCallException mapped = LangChain4jChatTransport.map(error);
                health.markFailure(route.provider().getId());
                recordUsage(request, route, null, OaAiUsageLog.STATUS_FAILED, mapped.errorCode(), safeMessage(mapped));
                if (firstDelta.get()) {
                    listener.onError(new ServiceException(mapped.errorCode() + " " + safeMessage(mapped), 502));
                } else {
                    done.set(false);
                    streamWithFallback(request, listener, ticket, routes, index + 1, start, inputTokens, buffer);
                }
            }
        });
        ticket.attach(handle);
    }

    // ---------------------------------------------------------------- probe（AI-M1-03）

    /** 测试连接：最小请求 + 错误分类，不落业务数据 */
    public AiProviderTestVo probe(Long providerId, String modelKey, String prompt) {
        AiProviderTestVo vo = new AiProviderTestVo();
        long start = System.currentTimeMillis();
        OaAiProvider provider = registry.requireProvider(providerId);
        String apiKey = registry.resolveApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            vo.setOk(false);
            vo.setErrorCategory(AiProviderError.AUTH_FAILED.name());
            vo.setMessage("渠道未配置可用密钥");
            return vo;
        }
        String key = (modelKey == null || modelKey.isBlank()) ? firstModelKey(providerId) : modelKey;
        if (key == null) {
            vo.setOk(false);
            vo.setErrorCategory(AiProviderError.UPSTREAM_ERROR.name());
            vo.setMessage("渠道下没有启用模型，请先配置模型或指定 modelKey");
            return vo;
        }
        vo.setModelKey(key);
        String testPrompt = (prompt == null || prompt.isBlank()) ? "ping" : prompt;
        Call call = new Call(provider.getBaseUrl(), apiKey, key, 0.0d, 16, List.of(Turn.user(testPrompt)));
        try {
            transport.complete(call);
            vo.setOk(true);
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setMessage("连接正常");
        } catch (RuntimeException e) {
            LlmCallException mapped = e instanceof LlmCallException callException ? callException : LangChain4jChatTransport.map(e);
            vo.setOk(false);
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setErrorCategory(categoryName(mapped));
            vo.setMessage(safeMessage(mapped));
        }
        return vo;
    }

    // ---------------------------------------------------------------- internals

    /** 有图片输入时只保留支持 vision 的候选，全部不支持即 400 */
    private List<Route> filterByCapability(List<Route> routes, List<Turn> turns) {
        boolean hasImage = turns != null && turns.stream().anyMatch(turn -> turn.images() != null && !turn.images().isEmpty());
        if (!hasImage) {
            return routes;
        }
        return filterByCapabilityCode(routes, AiCapability.VISION.code(), "当前模型不支持图片输入");
    }

    /** 仅保留具备指定能力的候选，全部不具备即 400 */
    private List<Route> filterByCapabilityCode(List<Route> routes, String capabilityCode, String message) {
        List<Route> capable = routes.stream()
            .filter(route -> AiCapability.supports(route.model().getCapability(), capabilityCode))
            .toList();
        if (capable.isEmpty()) {
            throw new ServiceException("MODEL_CAPABILITY_MISMATCH " + message, 400);
        }
        return capable;
    }

    private static int estimateTexts(List<String> texts) {
        int total = 0;
        if (texts != null) {
            for (String text : texts) {
                total += TokenEstimator.estimate(text);
            }
        }
        return total;
    }

    private Call callOf(Route route, CompletionRequest request) {
        Double temperature = request.temperature() != null ? request.temperature()
            : route.model().getDefaultTemperature() == null ? null : route.model().getDefaultTemperature().doubleValue();
        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : route.model().getMaxTokens();
        return new Call(route.provider().getBaseUrl(), route.apiKey(), route.modelKey(), temperature, maxTokens,
            request.turns(), request.tools());
    }

    private int reserveTokens(Integer maxTokens) {
        return maxTokens == null || maxTokens <= 0 ? 512 : Math.min(maxTokens, 2048);
    }

    private String firstModelKey(Long providerId) {
        List<OaAiModel> models = registry.modelsOf(providerId);
        return models.isEmpty() ? null : models.get(0).getModelKey();
    }

    private void recordUsage(CompletionRequest request, Route route, CompletionResult result,
                             String status, String errorCode, String errorMsg) {
        OaAiUsageLog log = new OaAiUsageLog();
        log.setUserId(request.userId());
        log.setUsername(request.username());
        log.setProviderId(route.provider().getId());
        log.setModelKey(route.modelKey());
        log.setBizType(request.bizType() == null ? AiBizType.CHAT.code() : request.bizType());
        log.setConversationId(request.conversationId());
        log.setTaskId(request.taskId());
        log.setPromptTokens(result == null ? 0 : result.promptTokens());
        log.setCompletionTokens(result == null ? 0 : result.completionTokens());
        log.setTotalTokens(result == null ? 0 : result.totalTokens());
        log.setLatencyMs(result == null ? null : (int) result.latencyMs());
        log.setStatus(status);
        log.setErrorCode(errorCode);
        log.setErrorMsg(truncate(errorMsg));
        log.setCreateTime(new Date());
        usageRecorder.record(log);
    }

    private void recordUsage(EmbeddingRequest request, Route route, int promptTokens,
                             String status, String errorCode, String errorMsg) {
        OaAiUsageLog log = new OaAiUsageLog();
        log.setUserId(request.userId());
        log.setUsername(request.username());
        log.setProviderId(route.provider().getId());
        log.setModelKey(route.modelKey());
        log.setBizType(request.bizType() == null ? AiBizType.RAG.code() : request.bizType());
        log.setTaskId(request.taskId());
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(0);
        log.setTotalTokens(promptTokens);
        log.setLatencyMs(0);
        log.setStatus(status);
        log.setErrorCode(errorCode);
        log.setErrorMsg(truncate(errorMsg));
        log.setCreateTime(new Date());
        usageRecorder.record(log);
    }

    private ServiceException upstreamFailure(LlmCallException last) {
        if (last == null) {
            return new ServiceException("LLM_UPSTREAM_ERROR 上游模型调用失败", 502);
        }
        int status = switch (last.category()) {
            case TIMEOUT -> 504;
            case MODEL_NOT_FOUND -> 400;
            case AUTH, BALANCE, UPSTREAM -> 502;
        };
        return new ServiceException(last.errorCode() + " " + safeMessage(last), status);
    }

    private String categoryName(LlmCallException e) {
        return switch (e.category()) {
            case AUTH -> AiProviderError.AUTH_FAILED.name();
            case BALANCE -> AiProviderError.BALANCE.name();
            case MODEL_NOT_FOUND -> AiProviderError.MODEL_NOT_FOUND.name();
            case TIMEOUT -> AiProviderError.TIMEOUT.name();
            case UPSTREAM -> AiProviderError.UPSTREAM_ERROR.name();
        };
    }

    /** 限额异常的错误码（消息首 token，与 SSE error code 口径一致） */
    private static String quotaErrorCode(ServiceException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "AI_QUOTA_EXCEEDED";
        }
        int space = message.indexOf(' ');
        return space > 0 ? message.substring(0, space) : message;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
