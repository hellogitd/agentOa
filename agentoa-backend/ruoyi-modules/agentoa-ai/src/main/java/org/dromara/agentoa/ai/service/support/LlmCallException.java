package org.dromara.agentoa.ai.service.support;

/**
 * 上游调用异常（网关归一化错误码，docs/21 AI-M1-06）。
 */
public class LlmCallException extends RuntimeException {

    public enum Category {
        /** 鉴权失败 */
        AUTH,
        /** 余额不足 */
        BALANCE,
        /** 模型不存在 */
        MODEL_NOT_FOUND,
        /** 超时 */
        TIMEOUT,
        /** 其他上游异常 */
        UPSTREAM
    }

    private final Category category;

    public LlmCallException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public LlmCallException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** 归一化错误码（对外） */
    public String errorCode() {
        return switch (category) {
            case AUTH -> "LLM_AUTH_FAILED";
            case BALANCE -> "LLM_BALANCE_INSUFFICIENT";
            case MODEL_NOT_FOUND -> "LLM_MODEL_NOT_FOUND";
            case TIMEOUT -> "LLM_TIMEOUT";
            case UPSTREAM -> "LLM_UPSTREAM_ERROR";
        };
    }
}
