package org.dromara.agentoa.attendance.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.service.ILeaveBalanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调休/年假批次滚动过期调度（AT-06）：复用 P1 批次二调度骨架（C 交付包同款 @Scheduled + 幂等扫描）。
 * 扫描幂等（EXPIRE:{batchId} 去重），小时级触发等效每日执行且漏扫自动补；
 * 手动兜底 POST /api/v1/attendance/leaves/expire-scan。
 */
@Component
@RequiredArgsConstructor
public class LeaveBatchExpiryDispatcher {

    private static final Long SYSTEM_OPERATOR = 0L;

    private final ILeaveBalanceService balanceService;

    @Scheduled(fixedDelay = 3_600_000)
    public void dispatch() {
        balanceService.expireBatches(SYSTEM_OPERATOR);
    }
}
