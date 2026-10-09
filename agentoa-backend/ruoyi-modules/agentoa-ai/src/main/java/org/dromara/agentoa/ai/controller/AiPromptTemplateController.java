package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiPromptTemplateBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiPromptTemplateVo;
import org.dromara.agentoa.ai.service.IAiPromptTemplateService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板（docs/21 AI-M2-05）：AI 生成内容仅供参考。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai")
public class AiPromptTemplateController {

    private final IAiPromptTemplateService promptTemplateService;

    /** 启用模板（登录即可，供对话选择） */
    @GetMapping("/chat/templates")
    public R<List<AiPromptTemplateVo>> enabledTemplates() {
        return R.ok(promptTemplateService.listEnabled());
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROMPT_QUERY)
    @GetMapping("/prompt-templates")
    public R<PageVo<AiPromptTemplateVo>> list(AiPageQuery page) {
        return R.ok(promptTemplateService.page(page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROMPT_ADD)
    @Log(title = "AI 提示词模板", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/prompt-templates")
    public R<AiPromptTemplateVo> add(@Validated @RequestBody AiPromptTemplateBo bo) {
        return R.ok(promptTemplateService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROMPT_EDIT)
    @Log(title = "AI 提示词模板", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/prompt-templates/{id}")
    public R<AiPromptTemplateVo> edit(@PathVariable Long id, @Validated @RequestBody AiPromptTemplateBo bo) {
        bo.setId(id);
        return R.ok(promptTemplateService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_PROMPT_REMOVE)
    @Log(title = "AI 提示词模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/prompt-templates/{id}")
    public R<Void> remove(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return R.ok();
    }
}
