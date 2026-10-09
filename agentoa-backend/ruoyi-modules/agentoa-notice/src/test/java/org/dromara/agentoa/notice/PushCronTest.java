package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.service.support.PushCron;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 定时推送 cron 受限子集（NC-04）：5 字段白名单解析与下一次执行时间计算。
 */
class PushCronTest {

    @Test
    void dailyAndIntervalSchedulesComputeNextSlot() {
        assertThat(PushCron.parse("0 10 * * *").nextAfter(LocalDateTime.of(2026, 3, 1, 9, 0)))
            .isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 0));
        assertThat(PushCron.parse("0 10 * * *").nextAfter(LocalDateTime.of(2026, 3, 1, 10, 0)))
            .isEqualTo(LocalDateTime.of(2026, 3, 2, 10, 0));
        assertThat(PushCron.parse("*/15 * * * *").nextAfter(LocalDateTime.of(2026, 3, 1, 10, 7)))
            .isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 15));
    }

    @Test
    void weeklyByDayAndMonthDayUseOrSemantics() {
        // 2026-03-02 为周一（1）
        assertThat(PushCron.parse("30 9 * * 1").nextAfter(LocalDateTime.of(2026, 3, 1, 12, 0)))
            .isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 30));
        // 日与周同时受限：任一命中即执行（标准 cron 语义）
        assertThat(PushCron.parse("0 8 15 * 1").nextAfter(LocalDateTime.of(2026, 3, 1, 0, 0)))
            .isEqualTo(LocalDateTime.of(2026, 3, 2, 8, 0));
    }

    @Test
    void yearlyScheduleScansWithinLimit() {
        assertThat(PushCron.parse("0 0 1 1 *").nextAfter(LocalDateTime.of(2026, 3, 1, 0, 0)))
            .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
        assertThat(PushCron.parse("0 0 30 2 *").nextAfter(LocalDateTime.of(2026, 3, 1, 0, 0)))
            .isNull();
    }

    @Test
    void unknownTokensAndBadShapesAreRejected() {
        assertThatThrownBy(() -> PushCron.parse("0 0 * JAN *"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_CRON_INVALID");
        assertThatThrownBy(() -> PushCron.parse("0 0 * * ?"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_CRON_INVALID");
        assertThatThrownBy(() -> PushCron.parse("0 0 * *"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_CRON_INVALID");
        assertThatThrownBy(() -> PushCron.parse("99 0 * * *"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_CRON_INVALID");
    }

    @Test
    void sundayAcceptsZeroAndSeven() {
        // 2026-03-01 为周日（7 归一化为 0）
        assertThat(PushCron.parse("0 8 * * 7").nextAfter(LocalDateTime.of(2026, 3, 1, 0, 0)))
            .isEqualTo(LocalDateTime.of(2026, 3, 1, 8, 0));
    }
}
