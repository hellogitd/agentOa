package org.dromara.agentoa.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.bo.EventBo;
import org.dromara.agentoa.collaboration.domain.bo.EventPageQuery;
import org.dromara.agentoa.collaboration.domain.vo.EventVo;
import org.dromara.agentoa.collaboration.service.ICalendarEventService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 日程接口（API 规范 9.1）：列表按时间范围与可见范围过滤，邀请接受/拒绝。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/calendar/events")
public class CalendarEventController {

    private final ICalendarEventService eventService;

    @GetMapping
    public R<PageVo<EventVo>> list(EventPageQuery query) {
        return R.ok(eventService.list(query, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<EventVo> detail(@PathVariable Long id) {
        return R.ok(eventService.detail(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:event:add")
    @Log(title = "日程", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<EventVo> add(@Validated @RequestBody EventBo bo) {
        return R.ok(eventService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:event:edit")
    @Log(title = "日程", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<EventVo> edit(@PathVariable Long id, @Validated @RequestBody EventBo bo) {
        return R.ok(eventService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:event:remove")
    @Log(title = "日程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        eventService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    @Log(title = "日程邀请", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/accept")
    public R<EventVo> accept(@PathVariable Long id) {
        return R.ok(eventService.respond(id, true, LoginHelper.getUserId()));
    }

    @Log(title = "日程邀请", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/reject")
    public R<EventVo> reject(@PathVariable Long id) {
        return R.ok(eventService.respond(id, false, LoginHelper.getUserId()));
    }
}
