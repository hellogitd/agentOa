package org.dromara.agentoa.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.OaDeptTreeVo;
import org.dromara.agentoa.hr.service.IHrDeptService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.system.domain.bo.SysDeptBo;
import org.dromara.system.domain.vo.SysDeptVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门组织接口（API 规范 3.1）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/departments")
public class HrDeptController {

    private final IHrDeptService deptService;

    /** 部门树 */
    @SaCheckPermission("hr:dept:list")
    @GetMapping("/tree")
    public R<List<OaDeptTreeVo>> tree() {
        return R.ok(deptService.selectDeptTree());
    }

    @SaCheckPermission("hr:dept:query")
    @GetMapping("/{deptId}")
    public R<SysDeptVo> get(@PathVariable Long deptId) {
        return R.ok(deptService.selectDeptById(deptId));
    }

    @SaCheckPermission("hr:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDeptBo bo) {
        deptService.insertDept(bo);
        return R.ok();
    }

    @SaCheckPermission("hr:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{deptId}")
    public R<Void> edit(@PathVariable Long deptId, @Validated @RequestBody SysDeptBo bo) {
        deptService.updateDept(deptId, bo);
        return R.ok();
    }

    /** 启停用 */
    @SaCheckPermission("hr:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{deptId}/status")
    public R<Void> status(@PathVariable Long deptId, @RequestBody SysDeptBo bo) {
        deptService.updateDeptStatus(deptId, bo.getStatus());
        return R.ok();
    }

    @SaCheckPermission("hr:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public R<Void> remove(@PathVariable Long deptId) {
        deptService.deleteDept(deptId);
        return R.ok();
    }
}
