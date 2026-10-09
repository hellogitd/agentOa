package org.dromara.agentoa.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.ExpenseTypeBo;
import org.dromara.agentoa.finance.domain.vo.ExpenseTypeVo;
import org.dromara.agentoa.finance.service.IExpenseTypeService;
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

/**
 * 费用类型接口（API 规范 6.2）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/expense-types")
public class FinanceExpenseTypeController {

    private final IExpenseTypeService expenseTypeService;

    @SaCheckPermission("fn:expense-type:list")
    @GetMapping
    public R<List<ExpenseTypeVo>> tree() {
        return R.ok(expenseTypeService.tree());
    }

    @SaCheckPermission("fn:expense-type:add")
    @Log(title = "费用类型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<ExpenseTypeVo> add(@Validated @RequestBody ExpenseTypeBo bo) {
        return R.ok(expenseTypeService.create(bo));
    }

    @SaCheckPermission("fn:expense-type:edit")
    @Log(title = "费用类型", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<ExpenseTypeVo> edit(@PathVariable Long id, @Validated @RequestBody ExpenseTypeBo bo) {
        return R.ok(expenseTypeService.update(id, bo));
    }

    @SaCheckPermission("fn:expense-type:remove")
    @Log(title = "费用类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        expenseTypeService.delete(id);
        return R.ok();
    }
}
