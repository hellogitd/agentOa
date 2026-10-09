package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.ScheduleAssignmentBo;
import org.dromara.agentoa.attendance.domain.vo.ScheduleAssignmentVo;
import org.dromara.agentoa.attendance.service.IScheduleService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班指派接口（P1，需求 AT-03）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/schedules")
public class AttendanceScheduleController {

    private final IScheduleService scheduleService;

    @SaCheckPermission("at:schedule:query")
    @GetMapping
    public R<List<ScheduleAssignmentVo>> list(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
                                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo) {
        return R.ok(scheduleService.selectAssignments(userId, dateFrom, dateTo));
    }

    @SaCheckPermission("at:schedule:add")
    @Log(title = "排班管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<List<ScheduleAssignmentVo>> add(@Validated @RequestBody ScheduleAssignmentBo bo) {
        return R.ok(scheduleService.assign(bo));
    }

    @SaCheckPermission("at:schedule:remove")
    @Log(title = "排班管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{assignmentId}")
    public R<Void> remove(@PathVariable Long assignmentId) {
        scheduleService.deleteAssignment(assignmentId);
        return R.ok();
    }
}
