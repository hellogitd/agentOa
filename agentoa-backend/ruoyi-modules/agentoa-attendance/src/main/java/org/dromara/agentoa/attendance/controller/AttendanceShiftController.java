package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.ShiftBo;
import org.dromara.agentoa.attendance.domain.vo.ShiftVo;
import org.dromara.agentoa.attendance.service.IShiftService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班次接口（API 规范 5.2）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/shifts")
public class AttendanceShiftController {

    private final IShiftService shiftService;

    @SaCheckPermission("at:shift:list")
    @GetMapping
    public R<PageVo<ShiftVo>> list(ShiftBo query, AttendancePageQuery page) {
        return R.ok(shiftService.selectPageShifts(query, page));
    }

    @SaCheckPermission("at:shift:list")
    @GetMapping("/all")
    public R<List<ShiftVo>> all() {
        return R.ok(shiftService.selectShifts());
    }

    @SaCheckPermission("at:shift:query")
    @GetMapping("/{shiftId}")
    public R<ShiftVo> get(@PathVariable Long shiftId) {
        return R.ok(shiftService.selectShiftById(shiftId));
    }

    @SaCheckPermission("at:shift:add")
    @Log(title = "班次管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<ShiftVo> add(@Validated @RequestBody ShiftBo bo) {
        return R.ok(shiftService.insertShift(bo));
    }

    @SaCheckPermission("at:shift:edit")
    @Log(title = "班次管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{shiftId}")
    public R<ShiftVo> edit(@PathVariable Long shiftId, @Validated @RequestBody ShiftBo bo) {
        return R.ok(shiftService.updateShift(shiftId, bo));
    }

    @SaCheckPermission("at:shift:remove")
    @Log(title = "班次管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftId}")
    public R<Void> remove(@PathVariable Long shiftId) {
        shiftService.deleteShift(shiftId);
        return R.ok();
    }
}
