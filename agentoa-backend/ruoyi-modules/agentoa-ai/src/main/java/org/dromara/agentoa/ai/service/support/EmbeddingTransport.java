package org.dromara.agentoa.ai.service.support;

import java.util.List;

/**
 * 向量化传输层抽象（docs/21 AI-M3-03）：默认实现走 LangChain4j OpenAI 兼容适配，
 * 测试可注入桩实现；业务代码禁止绕过 {@link LlmGateway} 直连 HTTP。
 */
public interface EmbeddingTransport {

    /** 批量向量化 */
    Result embed(Call call);

    /** 一次调用的完整入参 */
    record Call(String baseUrl, String apiKey, String modelKey, List<String> texts) {
    }

    /** 调用结果（vectors 与 texts 顺序一致） */
    record Result(List<float[]> vectors, Integer promptTokens) {
    }
}
