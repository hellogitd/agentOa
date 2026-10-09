package org.dromara.agentoa.attendance;

import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.service.support.WorkTimeCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 班次时间计算：固定班、午休、跨夜、弹性与宽限（docs/13 测试退出条件）。
 */
class WorkTimeCalculatorTest {

    private final WorkTimeCalculator calculator = new WorkTimeCalculator();

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private OaShift fixed() {
        OaShift shift = new OaShift();
        shift.setWorkStartTime(LocalTime.of(9, 0));
        shift.setWorkEndTime(LocalTime.of(18, 0));
        shift.setRestStartTime(LocalTime.of(12, 0));
        shift.setRestEndTime(LocalTime.of(13, 0));
        shift.setIsCrossDay(0);
        shift.setFlexibleMinutes(0);
        shift.setGraceMinutes(0);
        shift.setPunchWindowStart(120);
        shift.setPunchWindowEnd(240);
        return shift;
    }

    private OaShift night() {
        OaShift shift = new OaShift();
        shift.setWorkStartTime(LocalTime.of(22, 0));
        shift.setWorkEndTime(LocalTime.of(6, 0));
        shift.setIsCrossDay(1);
        shift.setFlexibleMinutes(0);
        shift.setGraceMinutes(0);
        shift.setPunchWindowStart(120);
        shift.setPunchWindowEnd(240);
        return shift;
    }

    @Test
    void fixedShiftExcludesRestFromScheduledMinutes() {
        assertThat(calculator.scheduledMinutes(fixed(), DAY)).isEqualTo(480);
    }

    @Test
    void lateAndEarlyAreComputedIndependentlyOfPunchWindow() {
        OaShift shift = fixed();
        assertThat(calculator.lateMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(9, 5)))).isEqualTo(5);
        assertThat(calculator.earlyMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(17, 50)))).isEqualTo(10);
        assertThat(calculator.withinPunchWindow(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(9, 5)))).isTrue();
    }

    @Test
    void graceMinutesAbsorbSmallDelays() {
        OaShift shift = fixed();
        shift.setGraceMinutes(10);
        assertThat(calculator.lateMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(9, 5)))).isZero();
        assertThat(calculator.earlyMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(17, 55)))).isZero();
    }

    @Test
    void flexibleShiftShiftsJudgementThreshold() {
        OaShift shift = fixed();
        shift.setFlexibleMinutes(30);
        assertThat(calculator.lateMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(9, 20)))).isZero();
        assertThat(calculator.lateMinutes(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(9, 35)))).isEqualTo(5);
    }

    @Test
    void crossNightShiftBelongsToShiftStartDate() {
        OaShift shift = night();
        assertThat(calculator.windowEnd(shift, DAY)).isEqualTo(LocalDateTime.of(DAY.plusDays(1), LocalTime.of(6, 0)));
        assertThat(calculator.attendanceDate(shift, LocalDateTime.of(DAY.plusDays(1), LocalTime.of(1, 30))))
            .isEqualTo(DAY);
        assertThat(calculator.attendanceDate(shift, LocalDateTime.of(DAY, LocalTime.of(23, 0)))).isEqualTo(DAY);
        assertThat(calculator.scheduledMinutes(shift, DAY)).isEqualTo(480);
    }

    @Test
    void punchWindowIsConfiguredSeparatelyFromLateThreshold() {
        OaShift shift = fixed();
        assertThat(calculator.withinPunchWindow(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(7, 0)))).isTrue();
        assertThat(calculator.withinPunchWindow(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(6, 59)))).isFalse();
        assertThat(calculator.withinPunchWindow(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(22, 0)))).isTrue();
        assertThat(calculator.withinPunchWindow(shift, DAY, LocalDateTime.of(DAY, LocalTime.of(22, 1)))).isFalse();
    }

    @Test
    void leaveMinutesExcludeRestAndClampToWorkWindow() {
        OaShift shift = fixed();
        assertThat(calculator.workMinutes(shift, DAY,
            LocalDateTime.of(DAY, LocalTime.of(9, 0)), LocalDateTime.of(DAY, LocalTime.of(18, 0)))).isEqualTo(480);
        assertThat(calculator.workMinutes(shift, DAY,
            LocalDateTime.of(DAY, LocalTime.of(11, 0)), LocalDateTime.of(DAY, LocalTime.of(14, 0)))).isEqualTo(120);
    }

    @Test
    void workedMinutesExcludeRestAndClampToWindow() {
        OaShift shift = fixed();
        assertThat(calculator.workedMinutes(shift, DAY,
            LocalDateTime.of(DAY, LocalTime.of(8, 30)), LocalDateTime.of(DAY, LocalTime.of(19, 0)))).isEqualTo(480);
        assertThat(calculator.workedMinutes(shift, DAY, null, LocalDateTime.of(DAY, LocalTime.of(18, 0))))
            .isZero();
    }
}
