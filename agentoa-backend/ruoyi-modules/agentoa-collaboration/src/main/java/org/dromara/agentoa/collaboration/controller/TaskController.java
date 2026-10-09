package org.dromara.agentoa.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.bo.CollaborationCommentBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskPageQuery;
import org.dromara.agentoa.collaboration.domain.bo.TaskProgressBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskStatusBo;
import org.dromara.agentoa.collaboration.domain.vo.BoardVo;
import org.dromara.agentoa.collaboration.domain.vo.TaskActivityVo;
import org.dromara.agentoa.collaboration.domain.vo.CollabTaskVo;
import org.dromara.agentoa.collaboration.service.ITaskService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务接口（API 规范 9.3）：列表/看板按可见范围过滤，状态机与终态保护在服务层。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final ITaskService taskService;

    @GetMapping
    public R<PageVo<CollabTaskVo>> list(TaskPageQuery query) {
        return R.ok(taskService.list(query, LoginHelper.getUserId()));
    }

    /** 看板视图（先于 /{id} 声明） */
    @GetMapping("/board")
    public R<BoardVo> board() {
        return R.ok(taskService.board(LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<CollabTaskVo> detail(@PathVariable Long id) {
        return R.ok(taskService.detail(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:add")
    @Log(title = "任务", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<CollabTaskVo> add(@Validated @RequestBody TaskBo bo) {
        return R.ok(taskService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:edit")
    @Log(title = "任务", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<CollabTaskVo> edit(@PathVariable Long id, @Validated @RequestBody TaskBo bo) {
        return R.ok(taskService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:remove")
    @Log(title = "任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        taskService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("cl:task:edit")
    @Log(title = "任务状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public R<CollabTaskVo> status(@PathVariable Long id, @Validated @RequestBody TaskStatusBo bo) {
        return R.ok(taskService.changeStatus(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:edit")
    @Log(title = "任务进度", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/progress")
    public R<CollabTaskVo> progress(@PathVariable Long id, @Validated @RequestBody TaskProgressBo bo) {
        return R.ok(taskService.changeProgress(id, bo, LoginHelper.getUserId()));
    }

    @Log(title = "任务评论", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{id}/comments")
    public R<TaskActivityVo> comment(@PathVariable Long id, @Validated @RequestBody CollaborationCommentBo bo) {
        return R.ok(taskService.comment(id, bo, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}/comments")
    public R<List<TaskActivityVo>> comments(@PathVariable Long id) {
        return R.ok(taskService.activities(id, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}/activities")
    public R<List<TaskActivityVo>> activities(@PathVariable Long id) {
        return R.ok(taskService.activities(id, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}/members")
    public R<List<CollabTaskVo.Member>> members(@PathVariable Long id) {
        return R.ok(taskService.members(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:edit")
    @Log(title = "任务协作者", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/members")
    public R<CollabTaskVo.Member> addMember(@PathVariable Long id, @RequestParam String userId) {
        return R.ok(taskService.addMember(id, userId, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:task:edit")
    @Log(title = "任务协作者", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable String userId) {
        taskService.removeMember(id, userId, LoginHelper.getUserId());
        return R.ok();
    }
}
