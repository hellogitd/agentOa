package org.dromara.agentoa.attendance.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;
import org.dromara.agentoa.attendance.domain.OaAttendanceMember;
import org.dromara.agentoa.attendance.domain.OaCalendar;
import org.dromara.agentoa.attendance.domain.OaHoliday;
import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.domain.OaShiftAssignment;
import org.dromara.agentoa.attendance.mapper.OaAttendanceGroupMapper;
import org.dromara.agentoa.attendance.mapper.OaAttendanceMemberMapper;
import org.dromara.agentoa.attendance.mapper.OaCalendarMapper;
import org.dromara.agentoa.attendance.mapper.OaHolidayMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftAssignmentMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 考勤规则解析：员工 + 日期 → 考勤组与班次；工作日判定优先组织日历，其次节假日登记，最后按考勤组工作日。
 * <p>
 * P1 排班指派（AT-03）：oa_shift_assignment 覆盖考勤组默认班次，未指派时回落到组班次。
 */
@Component
@RequiredArgsConstructor
public class ScheduleResolver {

    private final OaAttendanceMemberMapper memberMapper;
    private final OaAttendanceGroupMapper groupMapper;
    private final OaShiftMapper shiftMapper;
    private final OaCalendarMapper calendarMapper;
    private final OaHolidayMapper holidayMapper;
    private final OaShiftAssignmentMapper assignmentMapper;

    /** 考勤组 + 班次 + 工作日集合（周一=1） */
    public record Schedule(OaAttendanceGroup group, OaShift shift, Set<Integer> workDays) {
    }

    public Schedule resolve(Long userId, LocalDate date) {
        if (userId == null || date == null) {
            return null;
        }
        OaAttendanceMember member = memberMapper.selectOne(new LambdaQueryWrapper<OaAttendanceMember>()
            .eq(OaAttendanceMember::getUserId, userId)
            .le(OaAttendanceMember::getValidFrom, date)
            .and(wrapper -> wrapper.isNull(OaAttendanceMember::getValidTo)
                .or()
                .ge(OaAttendanceMember::getValidTo, date))
            .orderByDesc(OaAttendanceMember::getValidFrom)
            .last("LIMIT 1"));
        OaAttendanceGroup group = member == null ? null : groupMapper.selectById(member.getGroupId());
        if (group != null && "1".equals(group.getStatus())) {
            group = null;
        }
        Set<Integer> groupWorkDays = group == null ? null : parseWorkDays(group.getWorkDays());

        // P1：当日排班指派优先（覆盖组班次；无考勤组时按全周生效）
        OaShiftAssignment assignment = assignmentMapper.selectByUserAndDate(userId, date);
        if (assignment != null) {
            OaShift assignedShift = shiftMapper.selectById(assignment.getShiftId());
            if (assignedShift == null || "1".equals(assignedShift.getStatus())) {
                return null;
            }
            Set<Integer> workDays = groupWorkDays != null ? groupWorkDays : allWeekDays();
            return new Schedule(group, assignedShift, workDays);
        }

        if (group == null) {
            return null;
        }
        OaShift shift = shiftMapper.selectById(group.getShiftId());
        if (shift == null || "1".equals(shift.getStatus())) {
            return null;
        }
        return new Schedule(group, shift, groupWorkDays);
    }

    private static Set<Integer> allWeekDays() {
        Set<Integer> days = new HashSet<>();
        for (int i = 1; i <= 7; i++) {
            days.add(i);
        }
        return days;
    }

    /** 该日期是否为工作日（含调休上班） */
    public boolean isWorkday(Schedule schedule, LocalDate date) {
        OaCalendar row = calendarMapper.selectOne(new LambdaQueryWrapper<OaCalendar>()
            .eq(OaCalendar::getWorkDate, date)
            .last("LIMIT 1"));
        if (row != null) {
            return !"1".equals(row.getDayType());
        }
        OaHoliday holiday = holidayMapper.selectOne(new LambdaQueryWrapper<OaHoliday>()
            .eq(OaHoliday::getHolidayDate, date)
            .last("LIMIT 1"));
        if (holiday != null) {
            return "MAKEUP".equals(holiday.getHolidayType());
        }
        return schedule == null || schedule.workDays().contains(date.getDayOfWeek().getValue());
    }

    /** 仅按组织日历判断（无考勤组场景：报表、跨部门统计） */
    public boolean isWorkday(LocalDate date) {
        return isWorkday(null, date);
    }

    public static Set<Integer> parseWorkDays(String workDays) {
        Set<Integer> days = new HashSet<>();
        if (workDays == null || workDays.isBlank()) {
            return days;
        }
        for (String part : workDays.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int day = Integer.parseInt(trimmed);
                if (day >= 1 && day <= 7) {
                    days.add(day);
                }
            } catch (NumberFormatException ignored) {
                // 非法配置项跳过，避免整组规则不可用
            }
        }
        return days;
    }
}
