package org.dromara.agentoa.notice.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.notice.service.IScheduledPushService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时推送调度骨架（NC-04）：自研 @Scheduled + DB 租约（与 OutboxDispatcher 同款，单机 Compose 不引入 SnailJob）。
 * 手动兜底端点 POST /api/v1/notice/push/run 触发同一扫描（对齐 timeout-scan 先例）。
 */
@Component
@RequiredArgsConstructor
public class ScheduledPushDispatcher {

    private final IScheduledPushService pushService;

    @Scheduled(fixedDelay = 10_000)
    public void dispatch() {
        pushService.executeDue();
    }
}
