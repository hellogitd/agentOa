package org.dromara.agentoa.notice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.AnnouncementBo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.policy.NoticeAccessPolicy;
import org.dromara.agentoa.notice.domain.vo.AnnouncementVo;
import org.dromara.agentoa.notice.domain.vo.ReadStatusVo;
import org.dromara.agentoa.notice.service.IAnnouncementService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 公告接口（API 规范 7.1）：草稿、发布（受众快照）、撤回、已读与已读统计。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/notice")
public class NoticeController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IAnnouncementService announcementService;

    @GetMapping("/list")
    public R<PageVo<AnnouncementVo>> list(NoticePageQuery query) {
        return R.ok(announcementService.list(query, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<AnnouncementVo> detail(@PathVariable Long id) {
        return R.ok(announcementService.detail(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:notice:add")
    @Log(title = "公告", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AnnouncementVo> add(@Validated @RequestBody AnnouncementBo bo) {
        return R.ok(announcementService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:notice:edit")
    @Log(title = "公告", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AnnouncementVo> edit(@PathVariable Long id, @Validated @RequestBody AnnouncementBo bo) {
        return R.ok(announcementService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:notice:remove")
    @Log(title = "公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        announcementService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    /** 发布并生成受众快照（docs/05 7.1）：要求幂等键 */
    @SaCheckPermission("nt:notice:add")
    @Log(title = "公告发布", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/publish")
    public R<AnnouncementVo> publish(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                     @PathVariable Long id) {
        return R.ok(announcementService.publish(id, idempotencyKey, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:notice:recall")
    @Log(title = "公告撤回", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        announcementService.recall(id, LoginHelper.getUserId());
        return R.ok();
    }

    @GetMapping("/{id}/read-status")
    public R<ReadStatusVo> readStatus(@PathVariable Long id) {
        Long actor = LoginHelper.getUserId();
        if (!NoticeAccessPolicy.canReadStatus(LoginHelper.isSuperAdmin(), permissions(), actor, publisherId(id))) {
            throw new ServiceException("WF_FORBIDDEN 无权查看已读统计", 403);
        }
        return R.ok(announcementService.readStatus(id, actor));
    }

    @PutMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        announcementService.markRead(id, LoginHelper.getUserId());
        return R.ok();
    }

    private Long publisherId(Long id) {
        return announcementService.detail(id, LoginHelper.getUserId()).getPublisherId();
    }

    private Set<String> permissions() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getMenuPermission() == null ? Set.of() : loginUser.getMenuPermission();
    }
}
