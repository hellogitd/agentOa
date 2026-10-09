package org.dromara.agentoa.ai.service.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j OpenAI 兼容向量化传输（docs/21 §1.2）。
 * <p>
 * 超时口径同 {@link LangChain4jChatTransport}：连接 10s / 读 120s；禁用请求响应日志。
 */
@Component
public class LangChain4jEmbeddingTransport implements EmbeddingTransport {

    @Override
    public Result embed(Call call) {
        try {
            EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(call.baseUrl())
                .apiKey(call.apiKey())
                .modelName(call.modelKey())
                .httpClientBuilder(JdkHttpClient.builder()
                    .connectTimeout(LangChain4jChatTransport.CONNECT_TIMEOUT)
                    .readTimeout(LangChain4jChatTransport.READ_TIMEOUT))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
            List<TextSegment> segments = new ArrayList<>();
            for (String text : call.texts()) {
                segments.add(TextSegment.from(text == null ? "" : text));
            }
            Response<List<Embedding>> response = model.embedAll(segments);
            List<float[]> vectors = new ArrayList<>();
            if (response != null && response.content() != null) {
                for (Embedding embedding : response.content()) {
                    vectors.add(embedding.vector());
                }
            }
            TokenUsage usage = response == null ? null : response.tokenUsage();
            Integer promptTokens = usage == null || usage.inputTokenCount() == null ? null : usage.inputTokenCount();
            return new Result(vectors, promptTokens);
        } catch (RuntimeException e) {
            throw LangChain4jChatTransport.map(e);
        }
    }
}
