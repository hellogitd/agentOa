package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiModelBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiModelVo;
import org.dromara.agentoa.ai.service.IAiModelService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理（docs/21 AI-M1-04）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/models")
public class AiModelController {

    private final IAiModelService modelService;

    @SaCheckPermission(AiAccessPolicy.PERM_MODEL_QUERY)
    @GetMapping
    public R<PageVo<AiModelVo>> list(AiModelBo query, AiPageQuery page) {
        return R.ok(modelService.page(query, page));
    }

    /** 启用模型选择器（登录即可） */
    @GetMapping("/enabled")
    public R<List<AiModelVo>> enabled() {
        return R.ok(modelService.listEnabled());
    }

    @SaCheckPermission(AiAccessPolicy.PERM_MODEL_QUERY)
    @GetMapping("/{id}")
    public R<AiModelVo> get(@PathVariable Long id) {
        return R.ok(modelService.get(id));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_MODEL_ADD)
    @Log(title = "AI 模型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AiModelVo> add(@Validated @RequestBody AiModelBo bo) {
        return R.ok(modelService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_MODEL_EDIT)
    @Log(title = "AI 模型", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiModelVo> edit(@PathVariable Long id, @Validated @RequestBody AiModelBo bo) {
        bo.setId(id);
        return R.ok(modelService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_MODEL_REMOVE)
    @Log(title = "AI 模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        modelService.delete(id);
        return R.ok();
    }
}
