package org.dromara.agentoa.notice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.NoticeTemplateBo;
import org.dromara.agentoa.notice.domain.bo.TemplateSendBo;
import org.dromara.agentoa.notice.domain.vo.NoticeTemplateVo;
import org.dromara.agentoa.notice.service.INoticeTemplateService;
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
 * 通知模板接口（NC-04）：模板 CRUD 与按受众渲染发送（nt:template:*）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/notice/templates")
public class NoticeTemplateController {

    private final INoticeTemplateService templateService;

    @SaCheckPermission("nt:template:list")
    @GetMapping("/list")
    public R<PageVo<NoticeTemplateVo>> list(NoticePageQuery query) {
        return R.ok(templateService.list(query));
    }

    @SaCheckPermission("nt:template:list")
    @GetMapping("/{id}")
    public R<NoticeTemplateVo> detail(@PathVariable Long id) {
        return R.ok(templateService.detail(id));
    }

    @SaCheckPermission("nt:template:add")
    @Log(title = "通知模板", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<NoticeTemplateVo> add(@Validated @RequestBody NoticeTemplateBo bo) {
        return R.ok(templateService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:template:edit")
    @Log(title = "通知模板", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<NoticeTemplateVo> edit(@PathVariable Long id, @Validated @RequestBody NoticeTemplateBo bo) {
        return R.ok(templateService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("nt:template:remove")
    @Log(title = "通知模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    /** 按模板编码渲染并发送（受众 + 变量），返回投递人数 */
    @SaCheckPermission("nt:template:send")
    @Log(title = "通知模板发送", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{code}/send")
    public R<Integer> send(@PathVariable @Size(max = 64) String code,
                           @Validated @RequestBody TemplateSendBo bo) {
        return R.ok(templateService.send(code, bo, LoginHelper.getUserId()));
    }
}
