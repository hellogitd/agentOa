package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.enums.DayStatus;
import org.dromara.agentoa.attendance.domain.policy.AttendanceAccessPolicy;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.attendance.domain.vo.MonthlyReportVo;
import org.dromara.agentoa.attendance.mapper.OaAttendanceDayMapper;
import org.dromara.agentoa.attendance.service.IAttendanceReportService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考勤报表实现：员工仅本人，部门经理/HR 按授权范围，导出与列表同权。
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportServiceImpl implements IAttendanceReportService {

    private final OaAttendanceDayMapper dayMapper;
    private final WorkflowIdentityReadMapper identityMapper;

    @Override
    public PageVo<AttendanceDayVo> daily(DayQueryBo query, AttendancePageQuery page) {
        applyScope(query);
        LambdaQueryWrapper<OaAttendanceDay> wrapper = buildWrapper(query);
        wrapper.orderByDesc(OaAttendanceDay::getAttendanceDate).orderByAsc(OaAttendanceDay::getUserId);
        IPage<OaAttendanceDay> result = dayMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<AttendanceDayVo> records = new ArrayList<>();
        for (OaAttendanceDay row : result.getRecords()) {
            AttendanceDayVo vo = MapstructUtils.convert(row, AttendanceDayVo.class);
            if (vo != null) {
                vo.setNickname(identityMapper.selectNickName(row.getUserId()));
            }
            records.add(vo);
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<MonthlyReportVo> monthly(DayQueryBo query) {
        applyScope(query);
        if (query.getYearMonth() == null || query.getYearMonth().isBlank()) {
            throw new ServiceException("月份不能为空", 400);
        }
        LocalDate monthStart;
        try {
            monthStart = LocalDate.parse(query.getYearMonth() + "-01");
        } catch (Exception e) {
            throw new ServiceException("月份格式必须为 yyyy-MM", 400);
        }
        query.setDateFrom(monthStart);
        query.setDateTo(monthStart.plusMonths(1).minusDays(1));
        List<OaAttendanceDay> rows = dayMapper.selectList(buildWrapper(query));
        Map<Long, MonthlyReportVo> grouped = new LinkedHashMap<>();
        for (OaAttendanceDay row : rows) {
            MonthlyReportVo vo = grouped.computeIfAbsent(row.getUserId(), userId -> {
                MonthlyReportVo report = new MonthlyReportVo();
                report.setYearMonth(query.getYearMonth());
                report.setUserId(userId);
                report.setNickname(identityMapper.selectNickName(userId));
                report.setDeptId(row.getDeptId());
                report.setAttendanceDays(0);
                report.setLateCount(0);
                report.setEarlyCount(0);
                report.setAbsentCount(0);
                report.setLeaveMinutes(0);
                report.setOvertimeMinutes(0);
                report.setAbnormalCount(0);
                return report;
            });
            if (Integer.valueOf(DayStatus.PRESENT.code()).equals(row.getWorkStatus())
                || Integer.valueOf(DayStatus.LEAVE.code()).equals(row.getWorkStatus())) {
                vo.setAttendanceDays(vo.getAttendanceDays() + 1);
            }
            if (row.getLateMinutes() != null && row.getLateMinutes() > 0) {
                vo.setLateCount(vo.getLateCount() + 1);
            }
            if (row.getEarlyMinutes() != null && row.getEarlyMinutes() > 0) {
                vo.setEarlyCount(vo.getEarlyCount() + 1);
            }
            if (Integer.valueOf(DayStatus.ABSENT.code()).equals(row.getWorkStatus())) {
                vo.setAbsentCount(vo.getAbsentCount() + 1);
            }
            vo.setLeaveMinutes(vo.getLeaveMinutes() + value(row.getLeaveMinutes()));
            vo.setOvertimeMinutes(vo.getOvertimeMinutes() + value(row.getOvertimeMinutes()));
            if (Integer.valueOf(1).equals(row.getIsAbnormal())) {
                vo.setAbnormalCount(vo.getAbnormalCount() + 1);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    // ------------------------------------------------------------ internal

    /** 无查询他人权限时强制本人范围 */
    private void applyScope(DayQueryBo query) {
        Long current = LoginHelper.getUserId();
        Set<String> permissions = LoginHelper.getLoginUser() == null ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        boolean canSeeOthers = AttendanceAccessPolicy.canViewOthers(LoginHelper.isSuperAdmin(), permissions);
        if (!canSeeOthers && (query.getUserId() == null || !query.getUserId().equals(current))) {
            query.setUserId(current);
        }
    }

    private LambdaQueryWrapper<OaAttendanceDay> buildWrapper(DayQueryBo query) {
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
        if (Boolean.TRUE.equals(query.getAbnormalOnly())) {
            wrapper.eq(OaAttendanceDay::getIsAbnormal, 1);
        }
        return wrapper;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
