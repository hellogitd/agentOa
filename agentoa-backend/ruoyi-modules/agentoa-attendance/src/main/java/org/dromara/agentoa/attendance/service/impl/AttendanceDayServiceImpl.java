package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.OaOvertime;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.enums.DayStatus;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.attendance.mapper.OaAttendanceDayMapper;
import org.dromara.agentoa.attendance.mapper.OaOvertimeMapper;
import org.dromara.agentoa.attendance.mapper.OaPunchRecordMapper;
import org.dromara.agentoa.attendance.service.IAttendanceDayService;
import org.dromara.agentoa.attendance.service.support.ScheduleResolver;
import org.dromara.agentoa.attendance.service.support.WorkTimeCalculator;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.OaLeaveRequest;
import org.dromara.agentoa.workflow.mapper.OaLeaveRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.utils.MapstructUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 考勤日报实现：迟到、早退、缺卡是并存的计算结果，不用互斥枚举覆盖。
 */
@Service
@RequiredArgsConstructor
public class AttendanceDayServiceImpl implements IAttendanceDayService {

    private final OaAttendanceDayMapper dayMapper;
    private final OaPunchRecordMapper punchMapper;
    private final OaOvertimeMapper overtimeMapper;
    private final OaLeaveRequestMapper leaveRequestMapper;
    private final ScheduleResolver scheduleResolver;
    private final WorkTimeCalculator timeCalculator;
    private final WorkflowIdentityReadMapper identityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceDayVo recompute(Long userId, LocalDate date) {
        ScheduleResolver.Schedule schedule = scheduleResolver.resolve(userId, date);
        List<OaPunchRecord> punches = punchMapper.selectList(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, userId)
            .eq(OaPunchRecord::getPunchDate, date)
            .orderByAsc(OaPunchRecord::getPunchTime));
        LocalDateTime first = punches.isEmpty() ? null : punches.get(0).getPunchTime();
        LocalDateTime last = punches.isEmpty() ? null : punches.get(punches.size() - 1).getPunchTime();

        int scheduled = 0;
        int worked = 0;
        int late = 0;
        int early = 0;
        int leaveMinutes = 0;
        int workStatus = DayStatus.PENDING.code();
        Integer missingPunch = 0;
        List<String> abnormalReasons = new ArrayList<>();

        if (schedule == null) {
            abnormalReasons.add("未配置考勤规则");
        } else if (!scheduleResolver.isWorkday(schedule, date)) {
            workStatus = DayStatus.REST.code();
        } else {
            scheduled = timeCalculator.scheduledMinutes(schedule.shift(), date);
            leaveMinutes = computeLeaveMinutes(userId, date, schedule);
            worked = timeCalculator.workedMinutes(schedule.shift(), date, first, last);
            late = first == null ? 0 : timeCalculator.lateMinutes(schedule.shift(), date, first);
            early = last == null ? 0 : timeCalculator.earlyMinutes(schedule.shift(), date, last);
            boolean hasPunch = first != null;
            boolean onLeave = leaveMinutes > 0 && leaveMinutes >= scheduled;
            if (onLeave) {
                workStatus = DayStatus.LEAVE.code();
            } else if (hasPunch) {
                workStatus = DayStatus.PRESENT.code();
            } else {
                workStatus = DayStatus.ABSENT.code();
                abnormalReasons.add(leaveMinutes > 0 ? "请假未覆盖整日且无打卡" : "当日无打卡且无请假");
            }
            missingPunch = isMissingPunch(punches);
            if (missingPunch == 1) {
                boolean hasIn = punches.stream().anyMatch(punch -> Integer.valueOf(1).equals(punch.getPunchType()));
                boolean hasOut = punches.stream().anyMatch(punch -> Integer.valueOf(2).equals(punch.getPunchType()));
                abnormalReasons.add(hasIn ? "缺下班卡" : "缺上班卡");
                if (!hasIn && !hasOut) {
                    abnormalReasons.clear();
                    abnormalReasons.add("缺上班卡；缺下班卡");
                }
            }
            if (late > 0) {
                abnormalReasons.add("迟到" + late + "分钟");
            }
            if (early > 0) {
                abnormalReasons.add("早退" + early + "分钟");
            }
        }
        int overtimeMinutes = computeOvertimeMinutes(userId, date);
        int isAbnormal = abnormalReasons.isEmpty() ? 0 : 1;

        OaAttendanceDay row = dayMapper.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, userId)
            .eq(OaAttendanceDay::getAttendanceDate, date)
            .last("LIMIT 1"));
        if (row == null) {
            row = new OaAttendanceDay();
            row.setUserId(userId);
            row.setAttendanceDate(date);
            row.setRuleVersion(schedule == null ? 1 : 1);
            fillDay(row, schedule, first, last, workStatus, missingPunch, scheduled, worked,
                late, early, leaveMinutes, overtimeMinutes, isAbnormal, abnormalReasons);
            row.setCreateTime(new Date());
            row.setUpdateTime(new Date());
            dayMapper.insert(row);
        } else {
            fillDay(row, schedule, first, last, workStatus, missingPunch, scheduled, worked,
                late, early, leaveMinutes, overtimeMinutes, isAbnormal, abnormalReasons);
            row.setUpdateTime(new Date());
            dayMapper.updateById(row);
        }
        return toVo(row);
    }

    @Override
    public void recomputeRange(Long userId, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return;
        }
        for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
            recompute(userId, day);
        }
    }

    @Override
    public AttendanceDayVo selectDay(Long userId, LocalDate date) {
        OaAttendanceDay row = dayMapper.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, userId)
            .eq(OaAttendanceDay::getAttendanceDate, date)
            .last("LIMIT 1"));
        return row == null ? null : toVo(row);
    }

    @Override
    public PageVo<AttendanceDayVo> selectPage(DayQueryBo query, AttendancePageQuery page) {
        LambdaQueryWrapper<OaAttendanceDay> wrapper = new LambdaQueryWrapper<OaAttendanceDay>();
        if (query.getUserId() != null) {
            wrapper.eq(OaAttendanceDay::getUserId, query.getUserId());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(OaAttendanceDay::getDeptId, query.getDeptId());
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(OaAttendanceDay::getAttendanceDate, query.getDateFrom());
        }
        if (query.getDateTo() != null) {
            wrapper.le(OaAttendanceDay::getAttendanceDate, query.getDateTo());
        }
        if (query.getYearMonth() != null && !query.getYearMonth().isBlank()) {
            LocalDate monthStart = LocalDate.parse(query.getYearMonth() + "-01");
            wrapper.ge(OaAttendanceDay::getAttendanceDate, monthStart)
                .le(OaAttendanceDay::getAttendanceDate, monthStart.plusMonths(1).minusDays(1));
        }
        if (Boolean.TRUE.equals(query.getAbnormalOnly())) {
            wrapper.eq(OaAttendanceDay::getIsAbnormal, 1);
        }
        wrapper.orderByDesc(OaAttendanceDay::getAttendanceDate).orderByAsc(OaAttendanceDay::getUserId);
        IPage<OaAttendanceDay> result = dayMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<AttendanceDayVo> records = new ArrayList<>();
        for (OaAttendanceDay row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    // ------------------------------------------------------------ internal

    private void fillDay(OaAttendanceDay row, ScheduleResolver.Schedule schedule,
                         LocalDateTime first, LocalDateTime last, int workStatus, Integer missingPunch,
                         int scheduled, int worked, int late, int early, int leaveMinutes,
                         int overtimeMinutes, int isAbnormal, List<String> abnormalReasons) {
        row.setEmployeeId(identityMapper.selectEmployeeId(row.getUserId()));
        row.setDeptId(identityMapper.selectEmployeeDeptId(row.getUserId()));
        row.setGroupId(schedule == null ? null : schedule.group().getId());
        row.setShiftId(schedule == null ? null : schedule.shift().getId());
        row.setFirstPunchTime(first);
        row.setLastPunchTime(last);
        row.setWorkStatus(workStatus);
        row.setMissingPunch(missingPunch == null ? 0 : missingPunch);
        row.setScheduledMinutes(scheduled);
        row.setWorkedMinutes(worked);
        row.setLateMinutes(late);
        row.setEarlyMinutes(early);
        row.setLeaveMinutes(leaveMinutes);
        row.setOvertimeMinutes(overtimeMinutes);
        row.setIsAbnormal(isAbnormal);
        row.setAbnormalReason(abnormalReasons.isEmpty() ? null : String.join("；", abnormalReasons));
    }

    /** 有打卡但缺少上班或下班记录即为缺卡（补卡记录按补卡时段计入） */
    private int isMissingPunch(List<OaPunchRecord> punches) {
        if (punches.isEmpty()) {
            return 0;
        }
        boolean hasIn = punches.stream().anyMatch(punch -> Integer.valueOf(1).equals(punch.getPunchType())
            || Integer.valueOf(4).equals(punch.getPunchType()));
        boolean hasOut = punches.stream().anyMatch(punch -> Integer.valueOf(2).equals(punch.getPunchType())
            || Integer.valueOf(4).equals(punch.getPunchType()));
        return hasIn && hasOut ? 0 : 1;
    }

    /** 已通过请假与当日工作窗口（扣休息）的交集分钟 */
    private int computeLeaveMinutes(Long userId, LocalDate date, ScheduleResolver.Schedule schedule) {
        LocalDateTime windowStart = timeCalculator.windowStart(schedule.shift(), date);
        LocalDateTime windowEnd = timeCalculator.windowEnd(schedule.shift(), date);
        List<OaLeaveRequest> requests = leaveRequestMapper.selectList(new LambdaQueryWrapper<OaLeaveRequest>()
            .eq(OaLeaveRequest::getUserId, userId)
            .eq(OaLeaveRequest::getStatus, 3)
            .lt(OaLeaveRequest::getStartTime, windowEnd)
            .gt(OaLeaveRequest::getEndTime, windowStart));
        int total = 0;
        for (OaLeaveRequest request : requests) {
            total += timeCalculator.workMinutes(schedule.shift(), date, request.getStartTime(), request.getEndTime());
        }
        return total;
    }

    private int computeOvertimeMinutes(Long userId, LocalDate date) {
        List<OaOvertime> records = overtimeMapper.selectList(new LambdaQueryWrapper<OaOvertime>()
            .eq(OaOvertime::getUserId, userId)
            .eq(OaOvertime::getOvertimeDate, date)
            .eq(OaOvertime::getStatus, 3));
        int total = 0;
        for (OaOvertime record : records) {
            total += record.getDurationMinutes() == null ? 0 : record.getDurationMinutes();
        }
        return total;
    }

    private AttendanceDayVo toVo(OaAttendanceDay row) {
        AttendanceDayVo vo = MapstructUtils.convert(row, AttendanceDayVo.class);
        if (vo != null) {
            vo.setNickname(identityMapper.selectNickName(row.getUserId()));
        }
        return vo;
    }
}
