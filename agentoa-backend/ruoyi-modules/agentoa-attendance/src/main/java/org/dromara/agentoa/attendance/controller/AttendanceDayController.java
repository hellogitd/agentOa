package org.dromara.agentoa.attendance.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.policy.AttendanceAccessPolicy;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.attendance.service.IAttendanceDayService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

/**
 * 考勤日报接口（docs/13）：员工仅本人，经理/HR 按授权范围。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/days")
public class AttendanceDayController {

    private final IAttendanceDayService dayService;

    @GetMapping
    public R<PageVo<AttendanceDayVo>> list(DayQueryBo query, AttendancePageQuery page) {
        applyScope(query);
        return R.ok(dayService.selectPage(query, page));
    }

    @GetMapping("/today")
    public R<AttendanceDayVo> today() {
        return R.ok(dayService.selectDay(LoginHelper.getUserId(), LocalDate.now()));
    }

    private void applyScope(DayQueryBo query) {
        Set<String> permissions = LoginHelper.getLoginUser() == null ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        boolean canSeeOthers = AttendanceAccessPolicy.canViewOthers(LoginHelper.isSuperAdmin(), permissions);
        if (!canSeeOthers) {
            query.setUserId(LoginHelper.getUserId());
        } else if (query.getUserId() == null) {
            query.setUserId(LoginHelper.getUserId());
        }
    }
}
