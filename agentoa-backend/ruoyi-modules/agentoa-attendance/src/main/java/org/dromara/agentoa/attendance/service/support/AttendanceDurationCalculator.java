package org.dromara.agentoa.attendance.service.support;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.service.support.DurationCalculators;
import org.dromara.agentoa.workflow.spi.DurationCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假时长计算器（docs/13 核心规则）：按工作日历、班次和休息段计算，不接受客户端时长。
 * 用户未配置考勤组/班次时返回 null，由流程模块回退自然时长算法。
 */
@Component
@RequiredArgsConstructor
public class AttendanceDurationCalculator implements DurationCalculator {

    private final ScheduleResolver scheduleResolver;
    private final WorkTimeCalculator timeCalculator;
    private final DurationCalculators durationCalculators;

    @PostConstruct
    public void register() {
        durationCalculators.register(this);
    }

    @Override
    public Integer workMinutes(Long userId, LocalDateTime start, LocalDateTime end) {
        if (userId == null || start == null || end == null || !end.isAfter(start)) {
            return null;
        }
        ScheduleResolver.Schedule schedule = scheduleResolver.resolve(userId, start.toLocalDate());
        if (schedule == null) {
            return null;
        }
        int total = 0;
        for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
            if (!scheduleResolver.isWorkday(schedule, day)) {
                continue;
            }
            total += timeCalculator.workMinutes(schedule.shift(), day, start, end);
        }
        return total;
    }
}
