package org.dromara.agentoa.attendance.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaCorrection;
import org.dromara.agentoa.attendance.domain.OaLeaveType;
import org.dromara.agentoa.attendance.domain.OaOvertime;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
import org.dromara.agentoa.attendance.mapper.OaCorrectionMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveTypeMapper;
import org.dromara.agentoa.attendance.mapper.OaOvertimeMapper;
import org.dromara.agentoa.attendance.mapper.OaPunchRecordMapper;
import org.dromara.agentoa.attendance.service.IAttendanceDayService;
import org.dromara.agentoa.attendance.service.ILeaveBalanceService;
import org.dromara.agentoa.workflow.domain.OaCorrectionRequest;
import org.dromara.agentoa.workflow.domain.OaLeaveRequest;
import org.dromara.agentoa.workflow.domain.OaOvertimeRequest;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.mapper.OaCorrectionRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaLeaveRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaOvertimeRequestMapper;
import org.dromara.agentoa.workflow.service.support.FlowEventListenerRegistry;
import org.dromara.agentoa.workflow.spi.FlowEventListener;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 考勤回调承接（docs/13 第 6 步）：请假提交冻结额度、通过转已用、拒绝/撤销释放；
 * 加班/补卡通过后生成核定记录与补卡打卡记录并重算日报。账本动作全部在流程编排事务内执行。
 */
@Component
@RequiredArgsConstructor
public class AttendanceFlowListener implements FlowEventListener {

    /** 每月补卡上限（docs/02 AT-08，超限拒绝提交） */
    static final int MAX_CORRECTIONS_PER_MONTH = 3;

    private final FlowEventListenerRegistry listenerRegistry;
    private final OaLeaveRequestMapper leaveRequestMapper;
    private final OaOvertimeRequestMapper overtimeRequestMapper;
    private final OaCorrectionRequestMapper correctionRequestMapper;
    private final OaLeaveTypeMapper leaveTypeMapper;
    private final OaOvertimeMapper overtimeMapper;
    private final OaCorrectionMapper correctionMapper;
    private final OaPunchRecordMapper punchMapper;
    private final ILeaveBalanceService balanceService;
    private final IAttendanceDayService dayService;

    @PostConstruct
    public void register() {
        listenerRegistry.register(this);
    }

    @Override
    public void onBusinessValidated(BusinessType type, Long businessId, Long submitterUserId) {
        switch (type) {
            case LEAVE -> validateLeave(businessId);
            case CORRECTION -> validateCorrection(businessId);
            default -> {
                // 其他业务类型不涉及考勤账本
            }
        }
    }

    @Override
    public void onBusinessStarted(BusinessType type, Long businessId, Long instanceId, int submissionNo) {
        if (type != BusinessType.LEAVE) {
            return;
        }
        OaLeaveRequest request = requireLeave(businessId);
        balanceService.freeze("leave", businessId, submissionNo, request.getUserId(),
            yearOf(request), request.getLeaveType(), minutesOf(request), request.getUserId());
    }

    @Override
    public void onBusinessApproved(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        switch (type) {
            case LEAVE -> approveLeave(businessId, operatorUserId);
            case OVERTIME -> approveOvertime(businessId);
            case CORRECTION -> approveCorrection(businessId);
            default -> {
            }
        }
    }

    @Override
    public void onBusinessRejected(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        releaseOrCancel(type, businessId);
    }

    @Override
    public void onBusinessRevoked(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        releaseOrCancel(type, businessId);
    }

    // ------------------------------------------------------------ leave

    private void validateLeave(Long businessId) {
        OaLeaveRequest request = requireLeave(businessId);
        long knownType = leaveTypeMapper.selectCount(new LambdaQueryWrapper<OaLeaveType>()
            .eq(OaLeaveType::getTypeCode, request.getLeaveType()));
        if (knownType == 0) {
            throw new ServiceException("假种不存在: " + request.getLeaveType(), 400);
        }
        long overlapping = leaveRequestMapper.selectCount(new LambdaQueryWrapper<OaLeaveRequest>()
            .eq(OaLeaveRequest::getUserId, request.getUserId())
            .in(OaLeaveRequest::getStatus, 2, 3)
            .ne(OaLeaveRequest::getId, businessId)
            .lt(OaLeaveRequest::getStartTime, request.getEndTime())
            .gt(OaLeaveRequest::getEndTime, request.getStartTime()));
        if (overlapping > 0) {
            throw new ServiceException("WF_STATE_CONFLICT 请假时间与在途或已通过的申请重叠", 409);
        }
        Integer available = availableMinutes(request);
        if (available != null && available < minutesOf(request)) {
            throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 可用额度不足", 409);
        }
    }

    private void approveLeave(Long businessId, Long operatorUserId) {
        OaLeaveRequest request = requireLeave(businessId);
        balanceService.settle("leave", businessId, submissionNoOf(request), request.getUserId(),
            yearOf(request), request.getLeaveType(), minutesOf(request), operatorUserId);
        recomputeLeaveRange(request);
    }

    private void releaseOrCancel(BusinessType type, Long businessId) {
        switch (type) {
            case LEAVE -> {
                OaLeaveRequest request = requireLeave(businessId);
                balanceService.release("leave", businessId, submissionNoOf(request), request.getUserId(),
                    yearOf(request), request.getLeaveType(), minutesOf(request), request.getUserId());
                recomputeLeaveRange(request);
            }
            case OVERTIME -> {
                OaOvertime overtime = overtimeMapper.selectOne(new LambdaQueryWrapper<OaOvertime>()
                    .eq(OaOvertime::getOvertimeRequestId, businessId)
                    .last("LIMIT 1"));
                if (overtime != null) {
                    overtime.setStatus(7);
                    overtime.setUpdateTime(new Date());
                    overtimeMapper.updateById(overtime);
                    dayService.recompute(overtime.getUserId(), overtime.getOvertimeDate());
                }
            }
            case CORRECTION -> {
                OaCorrection correction = correctionMapper.selectOne(new LambdaQueryWrapper<OaCorrection>()
                    .eq(OaCorrection::getCorrectionRequestId, businessId)
                    .last("LIMIT 1"));
                if (correction != null) {
                    correction.setStatus(7);
                    correction.setUpdateTime(new Date());
                    correctionMapper.updateById(correction);
                    dayService.recompute(correction.getUserId(), correction.getAttendanceDate());
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ overtime

    private void approveOvertime(Long businessId) {
        OaOvertimeRequest request = overtimeRequestMapper.selectById(businessId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 加班申请不存在", 404);
        }
        long existing = overtimeMapper.selectCount(new LambdaQueryWrapper<OaOvertime>()
            .eq(OaOvertime::getOvertimeRequestId, businessId));
        if (existing > 0) {
            dayService.recompute(request.getUserId(), request.getOvertimeDate());
            return;
        }
        OaOvertime overtime = new OaOvertime();
        overtime.setUserId(request.getUserId());
        overtime.setEmployeeId(request.getEmployeeId());
        overtime.setOvertimeRequestId(request.getId());
        overtime.setOvertimeDate(request.getOvertimeDate());
        overtime.setStartTime(request.getStartTime());
        overtime.setEndTime(request.getEndTime());
        overtime.setDurationMinutes(request.getDurationMinutes() == null ? 0 : request.getDurationMinutes());
        overtime.setOvertimeType(request.getOvertimeType());
        overtime.setStatus(3);
        overtime.setCreateTime(new Date());
        overtime.setUpdateTime(new Date());
        try {
            overtimeMapper.insert(overtime);
        } catch (DuplicateKeyException e) {
            return;
        }
        dayService.recompute(request.getUserId(), request.getOvertimeDate());
    }

    // ------------------------------------------------------------ correction

    private void validateCorrection(Long businessId) {
        OaCorrectionRequest request = correctionRequestMapper.selectById(businessId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 补卡申请不存在", 404);
        }
        LocalDate monthStart = request.getAttendanceDate().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        long approved = correctionMapper.selectCount(new LambdaQueryWrapper<OaCorrection>()
            .eq(OaCorrection::getUserId, request.getUserId())
            .eq(OaCorrection::getStatus, 3)
            .ge(OaCorrection::getAttendanceDate, monthStart)
            .le(OaCorrection::getAttendanceDate, monthEnd));
        long running = correctionRequestMapper.selectCount(new LambdaQueryWrapper<OaCorrectionRequest>()
            .eq(OaCorrectionRequest::getUserId, request.getUserId())
            .eq(OaCorrectionRequest::getStatus, 2)
            .ge(OaCorrectionRequest::getAttendanceDate, monthStart)
            .le(OaCorrectionRequest::getAttendanceDate, monthEnd)
            .ne(OaCorrectionRequest::getId, businessId));
        if (approved + running >= MAX_CORRECTIONS_PER_MONTH) {
            throw new ServiceException("WF_STATE_CONFLICT 每月补卡上限为 " + MAX_CORRECTIONS_PER_MONTH + " 次", 409);
        }
    }

    private void approveCorrection(Long businessId) {
        OaCorrectionRequest request = correctionRequestMapper.selectById(businessId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 补卡申请不存在", 404);
        }
        long existing = correctionMapper.selectCount(new LambdaQueryWrapper<OaCorrection>()
            .eq(OaCorrection::getCorrectionRequestId, businessId));
        if (existing > 0) {
            dayService.recompute(request.getUserId(), request.getAttendanceDate());
            return;
        }
        OaCorrection correction = new OaCorrection();
        correction.setUserId(request.getUserId());
        correction.setEmployeeId(request.getEmployeeId());
        correction.setCorrectionRequestId(request.getId());
        correction.setAttendanceDate(request.getAttendanceDate());
        correction.setPunchType(request.getPunchType());
        correction.setCorrectedTime(request.getCorrectedTime());
        correction.setStatus(3);
        correction.setCreateTime(new Date());
        correction.setUpdateTime(new Date());
        try {
            correctionMapper.insert(correction);
        } catch (DuplicateKeyException e) {
            dayService.recompute(request.getUserId(), request.getAttendanceDate());
            return;
        }
        insertCorrectionPunch(request);
        dayService.recompute(request.getUserId(), request.getAttendanceDate());
    }

    /** 补卡不覆盖原始打卡，以申请 ID 关联补卡记录（uk_punch_correction 保证只生成一次） */
    private void insertCorrectionPunch(OaCorrectionRequest request) {
        long existing = punchMapper.selectCount(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getCorrectionRequestId, request.getId()));
        if (existing > 0) {
            return;
        }
        OaPunchRecord record = new OaPunchRecord();
        record.setUserId(request.getUserId());
        record.setEmployeeId(request.getEmployeeId());
        record.setPunchDate(request.getAttendanceDate());
        record.setPunchTime(request.getCorrectedTime());
        record.setPunchType(request.getPunchType());
        record.setCorrectionRequestId(request.getId());
        record.setIsLate(0);
        record.setLateMinutes(0);
        record.setIsEarly(0);
        record.setEarlyMinutes(0);
        record.setSource(1);
        record.setCreateTime(new Date());
        try {
            punchMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 重复消费：不重复生成补卡打卡
        }
    }

    // ------------------------------------------------------------ shared

    private OaLeaveRequest requireLeave(Long businessId) {
        OaLeaveRequest request = leaveRequestMapper.selectById(businessId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private int submissionNoOf(OaLeaveRequest request) {
        return request.getSubmissionNo() == null ? 0 : request.getSubmissionNo();
    }

    private int yearOf(OaLeaveRequest request) {
        return request.getStartTime().getYear();
    }

    private int minutesOf(OaLeaveRequest request) {
        return request.getDurationMinutes() == null ? 0 : request.getDurationMinutes();
    }

    /** 不计额度假种返回 null */
    private Integer availableMinutes(OaLeaveRequest request) {
        List<BalanceVo> balances = balanceService.selectBalances(request.getUserId(), yearOf(request));
        for (BalanceVo balance : balances) {
            if (balance.getLeaveType().equals(request.getLeaveType())) {
                return balance.getAvailableMinutes();
            }
        }
        return null;
    }

    private void recomputeLeaveRange(OaLeaveRequest request) {
        if (request.getStartTime() != null && request.getEndTime() != null) {
            dayService.recomputeRange(request.getUserId(), request.getStartTime(), request.getEndTime());
        }
    }
}
