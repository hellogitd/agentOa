package org.dromara.agentoa.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.BudgetBo;
import org.dromara.agentoa.finance.domain.vo.BudgetVo;
import org.dromara.agentoa.finance.service.IBudgetService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算接口（P1，API 规范 6.3）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/budgets")
public class FinanceBudgetController {

    private final IBudgetService budgetService;

    @SaCheckPermission("fn:budget:list")
    @GetMapping
    public R<List<BudgetVo>> list(BudgetBo query) {
        return R.ok(budgetService.selectBudgets(query));
    }

    @SaCheckPermission("fn:budget:query")
    @GetMapping("/{budgetId}")
    public R<BudgetVo> get(@PathVariable Long budgetId) {
        return R.ok(budgetService.selectBudget(budgetId));
    }

    /** 使用情况（总额/已用/冻结/剩余/预警） */
    @SaCheckPermission("fn:budget:query")
    @GetMapping("/{budgetId}/usage")
    public R<BudgetVo> usage(@PathVariable Long budgetId) {
        return R.ok(budgetService.selectBudget(budgetId));
    }

    @SaCheckPermission("fn:budget:add")
    @Log(title = "预算管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<BudgetVo> add(@Validated @RequestBody BudgetBo bo) {
        return R.ok(budgetService.createBudget(bo));
    }

    @SaCheckPermission("fn:budget:edit")
    @Log(title = "预算管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{budgetId}")
    public R<BudgetVo> edit(@PathVariable Long budgetId, @Validated @RequestBody BudgetBo bo) {
        return R.ok(budgetService.updateBudget(budgetId, bo));
    }

    @SaCheckPermission("fn:budget:remove")
    @Log(title = "预算管理", businessType = BusinessType.UPDATE)
    @DeleteMapping("/{budgetId}")
    public R<Void> remove(@PathVariable Long budgetId) {
        budgetService.closeBudget(budgetId);
        return R.ok();
    }
}
