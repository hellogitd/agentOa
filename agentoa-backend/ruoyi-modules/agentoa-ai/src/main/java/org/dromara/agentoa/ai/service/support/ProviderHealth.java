package org.dromara.agentoa.ai.service.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道健康度（docs/21 AI-M1-05）：失败渠道在冷却期内排到候选末尾。
 */
@Component
public class ProviderHealth {

    /** 冷却窗口（毫秒） */
    public static final long COOLDOWN_MS = 30_000L;

    private final Map<Long, Long> failedAt = new ConcurrentHashMap<>();

    public void markFailure(Long providerId) {
        if (providerId != null) {
            failedAt.put(providerId, System.currentTimeMillis());
        }
    }

    public void markSuccess(Long providerId) {
        if (providerId != null) {
            failedAt.remove(providerId);
        }
    }

    public boolean isHealthy(Long providerId) {
        Long at = failedAt.get(providerId);
        return at == null || System.currentTimeMillis() - at > COOLDOWN_MS;
    }
}
