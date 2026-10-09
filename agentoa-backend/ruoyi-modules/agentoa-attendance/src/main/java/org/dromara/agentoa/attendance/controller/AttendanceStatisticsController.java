package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.policy.AttendanceAccessPolicy;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.attendance.domain.vo.MonthlyReportVo;
import org.dromara.agentoa.attendance.service.IAttendanceReportService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * 考勤报表接口（API 规范 5.5）：日报、月报与导出，导出遵守与列表同一授权。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/statistics")
public class AttendanceStatisticsController {

    private final IAttendanceReportService reportService;

    @SaCheckPermission("at:report:list")
    @GetMapping("/daily")
    public R<PageVo<AttendanceDayVo>> daily(DayQueryBo query, AttendancePageQuery page) {
        return R.ok(reportService.daily(query, page));
    }

    @SaCheckPermission("at:report:list")
    @GetMapping("/monthly")
    public R<java.util.List<MonthlyReportVo>> monthly(DayQueryBo query) {
        return R.ok(reportService.monthly(query));
    }

    @SaCheckPermission("at:report:export")
    @Log(title = "考勤月报导出", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(DayQueryBo query, HttpServletResponse response) {
        Set<String> permissions = LoginHelper.getLoginUser() == null ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        if (!AttendanceAccessPolicy.canExport(LoginHelper.isSuperAdmin(), permissions)) {
            throw new ServiceException("WF_FORBIDDEN 无权导出考勤报表", 403);
        }
        ExcelUtil.exportExcel(reportService.monthly(query), "考勤月报", MonthlyReportVo.class, response);
    }
}
