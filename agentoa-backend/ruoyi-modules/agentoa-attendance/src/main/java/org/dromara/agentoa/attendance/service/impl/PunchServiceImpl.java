package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.PunchBo;
import org.dromara.agentoa.attendance.domain.enums.DayStatus;
import org.dromara.agentoa.attendance.domain.enums.PunchType;
import org.dromara.agentoa.attendance.domain.vo.PunchRecordVo;
import org.dromara.agentoa.attendance.domain.vo.PunchTodayVo;
import org.dromara.agentoa.attendance.domain.vo.PunchVo;
import org.dromara.agentoa.attendance.mapper.OaAttendanceDayMapper;
import org.dromara.agentoa.attendance.mapper.OaPunchRecordMapper;
import org.dromara.agentoa.attendance.service.IAttendanceDayService;
import org.dromara.agentoa.attendance.service.IPunchService;
import org.dromara.agentoa.attendance.service.support.ScheduleResolver;
import org.dromara.agentoa.attendance.service.support.WorkTimeCalculator;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 打卡实现：重复请求按 Idempotency-Key 返回首次结果；无幂等键的重复打卡保留原始记录，
 * 由日报计算取首次/末次有效记录。
 */
@Service
@RequiredArgsConstructor
public class PunchServiceImpl implements IPunchService {

    private static final String PUNCH_PATH = "POST /api/v1/attendance/punch";

    private final OaPunchRecordMapper punchMapper;
    private final OaAttendanceDayMapper dayMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final ScheduleResolver scheduleResolver;
    private final WorkTimeCalculator timeCalculator;
    private final IAttendanceDayService dayService;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PunchVo punch(PunchBo bo, String idempotencyKey) {
        Long userId = LoginHelper.getUserId();
        LocalDateTime now = currentTime();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = idempotencyKey.trim();
            String replayRef = idempotencyGuard.begin(userId, key, PUNCH_PATH,
                IdempotencyGuard.digest(bo.getPunchType() + "|" + now.toLocalDate()));
            if (replayRef != null) {
                OaPunchRecord stored = punchMapper.selectById(Long.valueOf(replayRef));
                if (stored != null) {
                    return toPunchVo(stored);
                }
            }
            OaPunchRecord record = doPunch(userId, bo, now);
            idempotencyGuard.complete(userId, key, String.valueOf(record.getId()));
            return toPunchVo(record);
        }
        return toPunchVo(doPunch(userId, bo, now));
    }

    @Override
    public PunchTodayVo today() {
        Long userId = LoginHelper.getUserId();
        LocalDateTime now = currentTime();
        PunchTodayVo vo = new PunchTodayVo();
        ScheduleContext context = resolveContext(userId, now);
        if (context == null) {
            vo.setAttendanceDate(now.toLocalDate().toString());
            vo.setTodayPunched(false);
            vo.setWorkStatus(DayStatus.PENDING.code());
            return vo;
        }
        vo.setAttendanceDate(context.date().toString());
        OaAttendanceDay day = dayMapper.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, userId)
            .eq(OaAttendanceDay::getAttendanceDate, context.date())
            .last("LIMIT 1"));
        List<OaPunchRecord> punches = punchMapper.selectList(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, userId)
            .eq(OaPunchRecord::getPunchDate, context.date())
            .orderByAsc(OaPunchRecord::getPunchTime));
        vo.setTodayPunched(!punches.isEmpty());
        vo.setPunchInTime(punches.isEmpty() ? null : punches.get(0).getPunchTime());
        vo.setPunchOutTime(punches.isEmpty() ? null : punches.get(punches.size() - 1).getPunchTime());
        if (day != null) {
            vo.setWorkStatus(day.getWorkStatus());
            vo.setScheduledMinutes(day.getScheduledMinutes());
            vo.setWorkedMinutes(day.getWorkedMinutes());
            vo.setLateMinutes(day.getLateMinutes());
            vo.setEarlyMinutes(day.getEarlyMinutes());
            vo.setLeaveMinutes(day.getLeaveMinutes());
            vo.setIsAbnormal(day.getIsAbnormal());
            vo.setAbnormalReason(day.getAbnormalReason());
        } else {
            vo.setWorkStatus(DayStatus.PENDING.code());
        }
        return vo;
    }

    @Override
    public PageVo<PunchRecordVo> selectRecords(Long userId, LocalDate dateFrom, LocalDate dateTo, AttendancePageQuery page) {
        LambdaQueryWrapper<OaPunchRecord> wrapper = new LambdaQueryWrapper<OaPunchRecord>();
        wrapper.eq(userId != null, OaPunchRecord::getUserId, userId)
            .ge(dateFrom != null, OaPunchRecord::getPunchDate, dateFrom)
            .le(dateTo != null, OaPunchRecord::getPunchDate, dateTo)
            .orderByDesc(OaPunchRecord::getPunchTime);
        IPage<OaPunchRecord> result = punchMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<PunchRecordVo> records = MapstructUtils.convert(result.getRecords(), PunchRecordVo.class);
        return PageVo.of(records == null ? List.of() : records, result.getTotal(),
            page.safePageNum(), page.safePageSize());
    }

    // ------------------------------------------------------------ internal

    /** 服务器接收时间（秒级精度，测试可覆写固定时钟） */
    protected LocalDateTime currentTime() {
        return LocalDateTime.now().withNano(0);
    }

    private OaPunchRecord doPunch(Long userId, PunchBo bo, LocalDateTime now) {
        boolean field = PunchType.isField(bo.getPunchType());
        if (field) {
            // P1 外勤（AT-09）：GPS 定位与现场照片缺一不可；定位仅为风险信号（docs/03 3.3）
            if (bo.getLng() == null || bo.getLat() == null) {
                throw new ServiceException("ATTENDANCE_LOCATION_INVALID 外勤打卡必须提供定位", 400);
            }
            if (bo.getPhotoFileId() == null) {
                throw new ServiceException("ATTENDANCE_LOCATION_INVALID 外勤打卡必须上传现场照片", 400);
            }
        }
        ScheduleContext context = resolveContext(userId, now);
        if (context == null) {
            if (!field) {
                throw new ServiceException("未配置考勤组或班次", 400);
            }
        }
        ScheduleResolver.Schedule schedule = context == null ? null : context.schedule();
        LocalDate date = context == null ? now.toLocalDate() : context.date();
        if (!field && !timeCalculator.withinPunchWindow(schedule.shift(), date, now)) {
            throw new ServiceException("ATTENDANCE_WINDOW_INVALID 不在可记录打卡窗口内", 400);
        }
        OaPunchRecord record = new OaPunchRecord();
        record.setUserId(userId);
        record.setEmployeeId(identityMapper.selectEmployeeId(userId));
        record.setPunchDate(date);
        record.setPunchTime(now);
        record.setPunchType(bo.getPunchType());
        record.setLng(bo.getLng());
        record.setLat(bo.getLat());
        record.setAccuracyMeters(bo.getAccuracyMeters());
        record.setAddress(bo.getAddress());
        record.setDevice(bo.getDevice());
        record.setWifiName(bo.getWifiName());
        record.setPhotoFileId(bo.getPhotoFileId());
        record.setIp(clientIp());
        record.setSource(bo.getSource() == null ? 1 : bo.getSource());
        int late = !field && bo.getPunchType() == 1 ? timeCalculator.lateMinutes(schedule.shift(), date, now) : 0;
        int early = !field && bo.getPunchType() == 2 ? timeCalculator.earlyMinutes(schedule.shift(), date, now) : 0;
        record.setIsLate(late > 0 ? 1 : 0);
        record.setLateMinutes(late);
        record.setIsEarly(early > 0 ? 1 : 0);
        record.setEarlyMinutes(early);
        record.setCreateTime(new Date());
        punchMapper.insert(record);
        dayService.recompute(userId, date);
        return record;
    }

    /** 解析打卡归属的考勤日：跨夜班凌晨打卡归属班次开始日期 */
    private ScheduleContext resolveContext(Long userId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        ScheduleResolver.Schedule schedule = scheduleResolver.resolve(userId, today);
        if (schedule == null) {
            ScheduleResolver.Schedule previous = scheduleResolver.resolve(userId, today.minusDays(1));
            return previous == null ? null : new ScheduleContext(previous, today.minusDays(1));
        }
        if (timeCalculator.isCrossDay(schedule.shift()) && now.toLocalTime().isBefore(schedule.shift().getWorkEndTime())) {
            ScheduleResolver.Schedule previous = scheduleResolver.resolve(userId, today.minusDays(1));
            if (previous != null) {
                return new ScheduleContext(previous, today.minusDays(1));
            }
        }
        return new ScheduleContext(schedule, today);
    }

    private PunchVo toPunchVo(OaPunchRecord record) {
        PunchVo vo = new PunchVo();
        vo.setPunchTime(record.getPunchTime());
        vo.setPunchType(record.getPunchType());
        vo.setIsLate(Integer.valueOf(1).equals(record.getIsLate()));
        vo.setLateMinutes(record.getLateMinutes());
        vo.setIsEarly(Integer.valueOf(1).equals(record.getIsEarly()));
        vo.setEarlyMinutes(record.getEarlyMinutes());
        vo.setLocation(record.getAddress());
        return vo;
    }

    private record ScheduleContext(ScheduleResolver.Schedule schedule, LocalDate date) {
    }

    /** 非 Web 上下文（任务/测试）返回 null */
    private String clientIp() {
        try {
            return org.dromara.common.core.utils.ServletUtils.getClientIP();
        } catch (Exception e) {
            return null;
        }
    }
}
