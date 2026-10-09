package org.dromara.agentoa.ai.service.support;

import java.util.List;

/**
 * 模型传输抽象（docs/21 §2.2）。默认实现走 LangChain4j OpenAI 兼容适配，
 * 测试可注入桩实现；业务代码禁止绕过 {@link LlmGateway} 直连 HTTP。
 * <p>
 * M5 扩展：{@code Call.tools} 声明可用工具，{@code Result.toolCalls} 返回模型发起的工具调用，
 * 工具结果以 {@code role=tool} 的 {@link Turn} 回填（原生 function calling）。
 */
public interface ChatTransport {

    /** 同步补全 */
    Result complete(Call call);

    /** 流式补全（逐段回调） */
    Handle stream(Call call, Listener listener);

    /** 一次调用的入参；tools 为空表示纯对话 */
    record Call(String baseUrl,
                String apiKey,
                String modelKey,
                Double temperature,
                Integer maxTokens,
                List<Turn> turns,
                List<ToolSpec> tools) {

        /** 兼容纯对话调用 */
        public Call(String baseUrl, String apiKey, String modelKey, Double temperature,
                    Integer maxTokens, List<Turn> turns) {
            this(baseUrl, apiKey, modelKey, temperature, maxTokens, turns, null);
        }
    }

    /** 工具声明（JSON Schema 描述入参） */
    record ToolSpec(String name, String description, String parametersJsonSchema) {
    }

    /** 模型发起的一次工具调用 */
    record ToolCall(String id, String name, String argumentsJson) {
    }

    /**
     * 一段对话：role = system/user/assistant/tool。
     * <p>
     * assistant 携带 {@code toolCalls} 表示模型发起工具调用；role=tool 表示工具结果回填。
     */
    record Turn(String role, String text, List<Image> images,
                List<ToolCall> toolCalls, String toolCallId, String toolName) {

        /** 纯文本对话轮（不含图片/工具调用） */
        public Turn(String role, String text, List<Image> images) {
            this(role, text, images, null, null, null);
        }

        public static Turn system(String text) {
            return new Turn("system", text, List.of());
        }

        public static Turn user(String text) {
            return new Turn("user", text, List.of());
        }

        public static Turn user(String text, List<Image> images) {
            return new Turn("user", text, images == null ? List.of() : images);
        }

        public static Turn assistant(String text) {
            return new Turn("assistant", text, List.of());
        }

        /** 模型发起工具调用（可带一段前置文本） */
        public static Turn assistantToolCalls(String text, List<ToolCall> toolCalls) {
            return new Turn("assistant", text, List.of(), toolCalls, null, null);
        }

        /** 工具执行结果回填 */
        public static Turn toolResult(String toolCallId, String toolName, String text) {
            return new Turn("tool", text, List.of(), null, toolCallId, toolName);
        }
    }

    /** 图片输入（base64 前的原始字节） */
    record Image(String mimeType, byte[] bytes) {
    }

    /** 补全结果；toolCalls 非空表示模型要求调用工具 */
    record Result(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens,
                  List<ToolCall> toolCalls) {

        public Result(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
            this(text, promptTokens, completionTokens, totalTokens, null);
        }
    }

    /** 流式回调 */
    interface Listener {
        void onDelta(String delta);

        void onComplete(Result result);

        void onError(Throwable error);
    }

    /** 流式句柄（支持取消） */
    interface Handle {
        void cancel();
    }
}
