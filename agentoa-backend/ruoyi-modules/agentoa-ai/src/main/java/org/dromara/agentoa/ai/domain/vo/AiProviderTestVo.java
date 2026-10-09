package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

/**
 * 测试连接结果（docs/21 AI-M1-03）。
 */
@Data
public class AiProviderTestVo {

    private boolean ok;

    private Long latencyMs;

    private String modelKey;

    /** 失败时：AUTH_FAILED/TIMEOUT/BALANCE/MODEL_NOT_FOUND/UPSTREAM_ERROR */
    private String errorCategory;

    private String message;
}
