package org.dromara.agentoa.ai.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiProviderBo;
import org.dromara.agentoa.ai.domain.bo.AiProviderStatusBo;
import org.dromara.agentoa.ai.domain.bo.AiProviderTestBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiProviderTestVo;
import org.dromara.agentoa.ai.domain.vo.AiProviderVo;
import org.dromara.agentoa.ai.service.IAiProviderService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 模型渠道（docs/21 AI-M1-01/02/03）：增删改与测试连接走审计。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/providers")
public class AiProviderController {

    private final IAiProviderService providerService;

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_QUERY)
    @GetMapping
    public R<PageVo<AiProviderVo>> list(AiPageQuery page) {
        return R.ok(providerService.page(page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_QUERY)
    @GetMapping("/{id}")
    public R<AiProviderVo> get(@PathVariable Long id) {
        return R.ok(providerService.get(id));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_ADD)
    @Log(title = "AI 模型渠道", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AiProviderVo> add(@Validated @RequestBody AiProviderBo bo) {
        return R.ok(providerService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_EDIT)
    @Log(title = "AI 模型渠道", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiProviderVo> edit(@PathVariable Long id, @Validated @RequestBody AiProviderBo bo) {
        bo.setId(id);
        return R.ok(providerService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_EDIT)
    @Log(title = "AI 模型渠道", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id, @Validated @RequestBody AiProviderStatusBo bo) {
        providerService.updateStatus(id, bo.getEnabled());
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_REMOVE)
    @Log(title = "AI 模型渠道", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        providerService.delete(id);
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROVIDER_TEST)
    @Log(title = "AI 模型渠道", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @PostMapping("/{id}/test")
    public R<AiProviderTestVo> test(@PathVariable Long id, @RequestBody(required = false) AiProviderTestBo bo) {
        return R.ok(providerService.test(id, bo));
    }
}
