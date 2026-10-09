package org.dromara.agentoa.ai.service.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LangChain4j OpenAI 兼容传输（docs/21 §1.2）：覆盖 DeepSeek/Qwen/Kimi/GLM/Ollama 等。
 * <p>
 * 超时口径（docs/21 AI-M1-06）：连接 10s / 读 120s；禁用请求响应日志，避免密钥与内容入日志。
 */
@Component
public class LangChain4jChatTransport implements ChatTransport {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    @Override
    public Result complete(Call call) {
        try {
            ChatResponse response = chatModel(call).chat(requestOf(call));
            AiMessage ai = response.aiMessage();
            String text = ai == null ? "" : String.valueOf(ai.text());
            return toResult(text, response.tokenUsage(), ai);
        } catch (RuntimeException e) {
            throw map(e);
        }
    }

    @Override
    public Handle stream(Call call, Listener listener) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        StringBuilder buffer = new StringBuilder();
        streamingModel(call).chat(requestOf(call),
            new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (cancelled.get() || partialResponse == null) {
                        return;
                    }
                    buffer.append(partialResponse);
                    listener.onDelta(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (cancelled.get()) {
                        return;
                    }
                    AiMessage ai = response == null ? null : response.aiMessage();
                    String text = ai != null && ai.text() != null ? ai.text() : buffer.toString();
                    listener.onComplete(toResult(text, response == null ? null : response.tokenUsage(), ai));
                }

                @Override
                public void onError(Throwable error) {
                    if (cancelled.get()) {
                        return;
                    }
                    listener.onError(map(error));
                }
            });
        return () -> cancelled.set(true);
    }

    /** 组装请求：消息 + 工具声明（M5） */
    private ChatRequest requestOf(Call call) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(toMessages(call));
        if (call.tools() != null && !call.tools().isEmpty()) {
            List<dev.langchain4j.agent.tool.ToolSpecification> specs = new ArrayList<>();
            for (ToolSpec tool : call.tools()) {
                specs.add(dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name(tool.name())
                    .description(tool.description() == null ? "" : tool.description())
                    .parameters(ToolSchemaMapper.toObjectSchema(tool.parametersJsonSchema()))
                    .build());
            }
            builder.toolSpecifications(specs);
        }
        return builder.build();
    }

    // ---------------------------------------------------------------- internals

    private OpenAiChatModel chatModel(Call call) {
        return OpenAiChatModel.builder()
            .baseUrl(call.baseUrl())
            .apiKey(call.apiKey())
            .modelName(call.modelKey())
            .httpClientBuilder(JdkHttpClient.builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT))
            .temperature(call.temperature())
            .maxTokens(call.maxTokens())
            .maxRetries(0)
            .logRequests(false)
            .logResponses(false)
            .build();
    }

    private OpenAiStreamingChatModel streamingModel(Call call) {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(call.baseUrl())
            .apiKey(call.apiKey())
            .modelName(call.modelKey())
            .httpClientBuilder(JdkHttpClient.builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT))
            .temperature(call.temperature())
            .maxTokens(call.maxTokens())
            .logRequests(false)
            .logResponses(false)
            .build();
    }

    private List<ChatMessage> toMessages(Call call) {
        List<ChatMessage> messages = new ArrayList<>();
        for (Turn turn : call.turns()) {
            switch (turn.role()) {
                case "system" -> messages.add(SystemMessage.from(turn.text() == null ? "" : turn.text()));
                case "assistant" -> messages.add(assistantMessage(turn));
                case "tool" -> messages.add(ToolExecutionResultMessage.from(
                    turn.toolCallId() == null ? "" : turn.toolCallId(),
                    turn.toolName() == null ? "" : turn.toolName(),
                    turn.text() == null ? "" : turn.text()));
                default -> {
                    List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
                    if (turn.text() != null && !turn.text().isBlank()) {
                        contents.add(TextContent.from(turn.text()));
                    }
                    if (turn.images() != null) {
                        for (dev.langchain4j.data.image.Image image : turn.images().stream().map(this::toImage).toList()) {
                            contents.add(ImageContent.from(image));
                        }
                    }
                    messages.add(UserMessage.from(contents.isEmpty() ? List.of(TextContent.from("")) : contents));
                }
            }
        }
        return messages;
    }

    /** assistant 轮：纯文本或带工具调用 */
    private ChatMessage assistantMessage(Turn turn) {
        String text = turn.text() == null ? "" : turn.text();
        if (turn.toolCalls() == null || turn.toolCalls().isEmpty()) {
            return AiMessage.from(text);
        }
        List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = new ArrayList<>();
        for (ToolCall call : turn.toolCalls()) {
            requests.add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id(call.id() == null ? "" : call.id())
                .name(call.name() == null ? "" : call.name())
                .arguments(call.argumentsJson() == null ? "{}" : call.argumentsJson())
                .build());
        }
        return text.isBlank() ? AiMessage.from(requests) : AiMessage.from(text, requests);
    }

    private dev.langchain4j.data.image.Image toImage(ChatTransport.Image image) {
        return dev.langchain4j.data.image.Image.builder()
            .base64Data(java.util.Base64.getEncoder().encodeToString(image.bytes()))
            .mimeType(image.mimeType())
            .build();
    }

    private Result toResult(String text, TokenUsage usage, AiMessage ai) {
        int prompt = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        int completion = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        int total = usage == null || usage.totalTokenCount() == null ? prompt + completion : usage.totalTokenCount();
        return new Result(text, prompt, completion, total, toolCallsOf(ai));
    }

    /** 模型发起的工具调用（无则为空列表） */
    private List<ToolCall> toolCallsOf(AiMessage ai) {
        if (ai == null || !ai.hasToolExecutionRequests()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (dev.langchain4j.agent.tool.ToolExecutionRequest request : ai.toolExecutionRequests()) {
            calls.add(new ToolCall(request.id(), request.name(), request.arguments()));
        }
        return calls;
    }

    /** 异常归一化（docs/21 AI-M1-06） */
    static LlmCallException map(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AuthenticationException) {
                return new LlmCallException(LlmCallException.Category.AUTH, "鉴权失败", error);
            }
            if (current instanceof ModelNotFoundException) {
                return new LlmCallException(LlmCallException.Category.MODEL_NOT_FOUND, "模型不存在", error);
            }
            if (current instanceof TimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof java.net.SocketTimeoutException) {
                return new LlmCallException(LlmCallException.Category.TIMEOUT, "上游超时", error);
            }
            if (current instanceof InvalidRequestException) {
                String message = safeMessage(current).toLowerCase(Locale.ROOT);
                if (message.contains("model")) {
                    return new LlmCallException(LlmCallException.Category.MODEL_NOT_FOUND, "模型不存在", error);
                }
                return new LlmCallException(LlmCallException.Category.UPSTREAM, safeMessage(current), error);
            }
            if (current instanceof HttpException http) {
                return new LlmCallException(classifyStatus(http.statusCode(), safeMessage(current)), safeMessage(current), error);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return new LlmCallException(LlmCallException.Category.UPSTREAM,
            error == null ? "上游异常" : safeMessage(error), error);
    }

    private static LlmCallException.Category classifyStatus(int status, String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("insufficient_quota") || lower.contains("quota") || lower.contains("balance")
            || lower.contains("余额") || status == 402) {
            return LlmCallException.Category.BALANCE;
        }
        if (status == 401 || status == 403) {
            return LlmCallException.Category.AUTH;
        }
        if (status == 404) {
            return LlmCallException.Category.MODEL_NOT_FOUND;
        }
        return LlmCallException.Category.UPSTREAM;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
