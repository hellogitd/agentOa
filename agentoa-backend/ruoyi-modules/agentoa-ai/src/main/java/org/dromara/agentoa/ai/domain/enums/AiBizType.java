package org.dromara.agentoa.ai.domain.enums;

/**
 * AI 调用业务类型（oa_ai_usage_log.biz_type）。
 */
public enum AiBizType {

    CHAT("chat"),
    RAG("rag"),
    COPILOT("copilot"),
    AGENT("agent"),
    PROBE("probe");

    private final String code;

    AiBizType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
