package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.AttendanceGroupBo;
import org.dromara.agentoa.attendance.domain.bo.AttendanceMemberBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.AttendanceGroupVo;
import org.dromara.agentoa.attendance.domain.vo.AttendanceMemberVo;
import org.dromara.agentoa.attendance.service.IAttendanceGroupService;
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
 * 考勤组接口（API 规范 5.2 / docs/13）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/groups")
public class AttendanceGroupController {

    private final IAttendanceGroupService groupService;

    @SaCheckPermission("at:group:list")
    @GetMapping
    public R<PageVo<AttendanceGroupVo>> list(AttendanceGroupBo query, AttendancePageQuery page) {
        return R.ok(groupService.selectPageGroups(query, page));
    }

    @SaCheckPermission("at:group:list")
    @GetMapping("/all")
    public R<List<AttendanceGroupVo>> all() {
        return R.ok(groupService.selectGroups());
    }

    @SaCheckPermission("at:group:query")
    @GetMapping("/{groupId}")
    public R<AttendanceGroupVo> get(@PathVariable Long groupId) {
        return R.ok(groupService.selectGroupById(groupId));
    }

    @SaCheckPermission("at:group:add")
    @Log(title = "考勤组", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AttendanceGroupVo> add(@Validated @RequestBody AttendanceGroupBo bo) {
        return R.ok(groupService.insertGroup(bo));
    }

    @SaCheckPermission("at:group:edit")
    @Log(title = "考勤组", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{groupId}")
    public R<AttendanceGroupVo> edit(@PathVariable Long groupId, @Validated @RequestBody AttendanceGroupBo bo) {
        return R.ok(groupService.updateGroup(groupId, bo));
    }

    @SaCheckPermission("at:group:remove")
    @Log(title = "考勤组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{groupId}")
    public R<Void> remove(@PathVariable Long groupId) {
        groupService.deleteGroup(groupId);
        return R.ok();
    }

    @SaCheckPermission("at:group:query")
    @GetMapping("/{groupId}/members")
    public R<PageVo<AttendanceMemberVo>> members(@PathVariable Long groupId, AttendancePageQuery page) {
        return R.ok(groupService.selectMembers(groupId, page));
    }

    @SaCheckPermission("at:group:edit")
    @Log(title = "考勤组成员", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{groupId}/members")
    public R<AttendanceMemberVo> addMember(@PathVariable Long groupId,
                                           @Validated @RequestBody AttendanceMemberBo bo) {
        return R.ok(groupService.addMember(groupId, bo));
    }

    @SaCheckPermission("at:group:edit")
    @Log(title = "考勤组成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{groupId}/members/{memberId}")
    public R<Void> removeMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        groupService.removeMember(groupId, memberId);
        return R.ok();
    }
}
