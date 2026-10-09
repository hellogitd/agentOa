package org.dromara.agentoa.attendance.service.support;

import org.dromara.agentoa.attendance.domain.OaShift;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班次时间计算（docs/13 核心规则）：存储 UTC、展示按组织时区；跨夜班归属班次开始日期；
 * 记录窗口与迟到/早退阈值分别配置；迟到、早退、缺卡是计算结果，不丢弃原始记录。
 * <p>
 * 弹性班：判定阈值整体平移 flexibleMinutes（晚到不计迟到、晚走不计早退）；
 * 宽限 graceMinutes 在阈值两端各让渡一次。
 */
@Component
public class WorkTimeCalculator {

    public boolean isCrossDay(OaShift shift) {
        return Integer.valueOf(1).equals(shift.getIsCrossDay())
            || shift.getWorkEndTime().isBefore(shift.getWorkStartTime());
    }

    public LocalDateTime windowStart(OaShift shift, LocalDate day) {
        return LocalDateTime.of(day, shift.getWorkStartTime());
    }

    public LocalDateTime windowEnd(OaShift shift, LocalDate day) {
        LocalDate endDay = isCrossDay(shift) ? day.plusDays(1) : day;
        return LocalDateTime.of(endDay, shift.getWorkEndTime());
    }

    /** 打卡时间归属的考勤日期（跨夜班归属班次开始日期） */
    public LocalDate attendanceDate(OaShift shift, LocalDateTime time) {
        if (isCrossDay(shift) && time.toLocalTime().isBefore(shift.getWorkEndTime())) {
            return time.toLocalDate().minusDays(1);
        }
        return time.toLocalDate();
    }

    public LocalDateTime lateThreshold(OaShift shift, LocalDate day) {
        return windowStart(shift, day).plusMinutes(flex(shift) + grace(shift));
    }

    public LocalDateTime earlyThreshold(OaShift shift, LocalDate day) {
        return windowEnd(shift, day).plusMinutes(flex(shift)).minusMinutes(grace(shift));
    }

    public int lateMinutes(OaShift shift, LocalDate day, LocalDateTime firstPunch) {
        if (firstPunch == null) {
            return 0;
        }
        long diff = Duration.between(lateThreshold(shift, day), firstPunch).toMinutes();
        return (int) Math.max(0, diff);
    }

    public int earlyMinutes(OaShift shift, LocalDate day, LocalDateTime lastPunch) {
        if (lastPunch == null) {
            return 0;
        }
        long diff = Duration.between(lastPunch, earlyThreshold(shift, day)).toMinutes();
        return (int) Math.max(0, diff);
    }

    /** 是否在可记录窗口内（与迟到/早退判定相互独立） */
    public boolean withinPunchWindow(OaShift shift, LocalDate day, LocalDateTime time) {
        LocalDateTime from = windowStart(shift, day).minusMinutes(valueOr(shift.getPunchWindowStart(), 120));
        LocalDateTime to = windowEnd(shift, day).plusMinutes(valueOr(shift.getPunchWindowEnd(), 240));
        return !time.isBefore(from) && !time.isAfter(to);
    }

    /** 计划工作分钟 = 工作窗口 - 休息段 */
    public int scheduledMinutes(OaShift shift, LocalDate day) {
        LocalDateTime start = windowStart(shift, day);
        LocalDateTime end = windowEnd(shift, day);
        long total = Duration.between(start, end).toMinutes();
        total -= restMinutes(shift, day, start, end);
        return (int) Math.max(0, total);
    }

    /** 实际出勤分钟 = [first..last] 与工作窗口（扣休息）的交集 */
    public int workedMinutes(OaShift shift, LocalDate day, LocalDateTime first, LocalDateTime last) {
        if (first == null || last == null || !last.isAfter(first)) {
            return 0;
        }
        LocalDateTime start = windowStart(shift, day);
        LocalDateTime end = windowEnd(shift, day);
        long minutes = overlapMinutes(first, last, start, end);
        minutes -= overlapMinutes(first, last, restStart(shift, day), restEnd(shift, day));
        return (int) Math.max(0, minutes);
    }

    /** 有效时长（分钟）：给定区间与该班次工作日工作窗口（扣休息）的交集 */
    public int workMinutes(OaShift shift, LocalDate day, LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = windowStart(shift, day);
        LocalDateTime end = windowEnd(shift, day);
        long minutes = overlapMinutes(from, to, start, end);
        minutes -= overlapMinutes(from, to, restStart(shift, day), restEnd(shift, day));
        return (int) Math.max(0, minutes);
    }

    private long restMinutes(OaShift shift, LocalDate day, LocalDateTime from, LocalDateTime to) {
        return overlapMinutes(from, to, restStart(shift, day), restEnd(shift, day));
    }

    private LocalDateTime restStart(OaShift shift, LocalDate day) {
        return shift.getRestStartTime() == null ? null : LocalDateTime.of(day, shift.getRestStartTime());
    }

    private LocalDateTime restEnd(OaShift shift, LocalDate day) {
        return shift.getRestEndTime() == null ? null : LocalDateTime.of(day, shift.getRestEndTime());
    }

    private long overlapMinutes(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return 0;
        }
        LocalDateTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDateTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
    }

    private int flex(OaShift shift) {
        return valueOr(shift.getFlexibleMinutes(), 0);
    }

    private int grace(OaShift shift) {
        return valueOr(shift.getGraceMinutes(), 0);
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
