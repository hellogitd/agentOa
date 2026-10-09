package org.dromara.agentoa.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.CategoryBo;
import org.dromara.agentoa.workflow.domain.vo.CategoryVo;
import org.dromara.agentoa.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf/categories")
public class WfCategoryController {

    private final IWorkflowTemplateService templateService;

    @GetMapping
    public R<List<CategoryVo>> list() {
        return R.ok(templateService.selectCategories());
    }

    @SaCheckPermission("wf:category:add")
    @Log(title = "流程分类", businessType = BusinessType.INSERT)
    @PostMapping
    public R<CategoryVo> add(@Validated @RequestBody CategoryBo bo) {
        return R.ok(templateService.createCategory(bo));
    }

    @SaCheckPermission("wf:category:edit")
    @Log(title = "流程分类", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{categoryId}")
    public R<CategoryVo> edit(@PathVariable Long categoryId, @Validated @RequestBody CategoryBo bo) {
        return R.ok(templateService.updateCategory(categoryId, bo));
    }

    @SaCheckPermission("wf:category:remove")
    @Log(title = "流程分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryId}")
    public R<Void> remove(@PathVariable Long categoryId) {
        templateService.deleteCategory(categoryId);
        return R.ok();
    }
}
