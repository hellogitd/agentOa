package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQuotaBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiQuotaVo;
import org.dromara.agentoa.ai.service.IAiQuotaService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用量配额（docs/21 AI-M1-08）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/quotas")
public class AiQuotaController {

    private final IAiQuotaService quotaService;

    @SaCheckPermission(AiAccessPolicy.PERM_QUOTA_LIST)
    @GetMapping
    public R<PageVo<AiQuotaVo>> list(AiPageQuery page) {
        return R.ok(quotaService.page(page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_QUOTA_EDIT)
    @Log(title = "AI 用量配额", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AiQuotaVo> add(@Validated @RequestBody AiQuotaBo bo) {
        return R.ok(quotaService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_QUOTA_EDIT)
    @Log(title = "AI 用量配额", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiQuotaVo> edit(@PathVariable Long id, @Validated @RequestBody AiQuotaBo bo) {
        bo.setId(id);
        return R.ok(quotaService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_QUOTA_EDIT)
    @Log(title = "AI 用量配额", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        quotaService.delete(id);
        return R.ok();
    }
}
