package org.dromara.agentoa.ai.domain.enums;

/**
 * 测试连接错误分类（docs/21 AI-M1-03）。
 */
public enum AiProviderError {

    AUTH_FAILED("鉴权失败"),
    TIMEOUT("连接超时"),
    BALANCE("余额不足"),
    MODEL_NOT_FOUND("模型不存在"),
    UPSTREAM_ERROR("上游异常");

    private final String label;

    AiProviderError(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
