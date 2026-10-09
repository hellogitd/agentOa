package org.dromara.agentoa.notice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.vo.OutboxEventVo;
import org.dromara.agentoa.notice.service.IOutboxAdminService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息事件管理（docs/15）：失败事件可查询、可人工重投并留下审计。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/messages/outbox")
public class OutboxAdminController {

    private final IOutboxAdminService outboxAdminService;

    @SaCheckPermission("nt:outbox:list")
    @GetMapping
    public R<PageVo<OutboxEventVo>> list(@RequestParam(required = false) String eventStatus, NoticePageQuery page) {
        return R.ok(outboxAdminService.list(eventStatus, page));
    }

    @SaCheckPermission("nt:outbox:redeliver")
    @Log(title = "消息事件重投", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/redeliver")
    public R<Void> redeliver(@PathVariable Long id) {
        outboxAdminService.redeliver(id, LoginHelper.getUserId());
        return R.ok();
    }
}
