package org.dromara.agentoa.notice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.ScheduledPushBo;
import org.dromara.agentoa.notice.domain.vo.PushRunVo;
import org.dromara.agentoa.notice.domain.vo.ScheduledPushVo;
import org.dromara.agentoa.notice.service.IScheduledPushService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时推送接口（NC-04）：任务 CRUD、暂停/恢复、执行历史与手动到期扫描（nt:schedule:*）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/notice/push")
public class NoticeScheduleController {

    private final IScheduledPushService pushService;

    @SaCheckPermission("nt:schedule:list")
    @GetMapping("/list")
    public R<PageVo<ScheduledPushVo>> list(NoticePageQuery query) {
        return R.ok(pushService.list(query));
    }

    @SaCheckPermission("nt:schedule:list")
    @GetMapping("/{id}")
    public R<ScheduledPushVo> detail(@PathVariable Long id) {
        return R.ok(pushService.detail(id));
    }

    @SaCheckPermission("nt:schedule:add")
    @Log(title = "定时推送", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<ScheduledPushVo> add(@Validated @RequestBody ScheduledPushBo bo) {
        return R.ok(pushService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:schedule:edit")
    @Log(title = "定时推送", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<ScheduledPushVo> edit(@PathVariable Long id, @Validated @RequestBody ScheduledPushBo bo) {
        return R.ok(pushService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:schedule:remove")
    @Log(title = "定时推送", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        pushService.delete(id);
        return R.ok();
    }

    @SaCheckPermission("nt:schedule:edit")
    @Log(title = "定时推送", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id) {
        pushService.pause(id);
        return R.ok();
    }

    @SaCheckPermission("nt:schedule:edit")
    @Log(title = "定时推送", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/resume")
    public R<Void> resume(@PathVariable Long id) {
        pushService.resume(id);
        return R.ok();
    }

    @SaCheckPermission("nt:schedule:list")
    @GetMapping("/{id}/runs")
    public R<PageVo<PushRunVo>> runs(@PathVariable Long id, NoticePageQuery query) {
        return R.ok(pushService.runs(id, query));
    }

    /** 到期扫描（幂等，调度器与运维手动兜底共用，对齐 timeout-scan 先例） */
    @SaCheckPermission("nt:schedule:run")
    @Log(title = "定时推送", businessType = BusinessType.UPDATE)
    @PostMapping("/run")
    public R<Integer> run() {
        return R.ok(pushService.executeDue());
    }
}
