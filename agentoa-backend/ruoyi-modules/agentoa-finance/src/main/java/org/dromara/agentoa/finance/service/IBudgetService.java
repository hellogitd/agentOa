package org.dromara.agentoa.finance.service;

import org.dromara.agentoa.finance.domain.bo.BudgetBo;
import org.dromara.agentoa.finance.domain.vo.BudgetVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算服务（P1，需求 FN-03）。可用额度 = 总额 - 已用 - 冻结；
 * 提交冻结、通过转已用、拒绝/撤销释放，账本 event_key 幂等、条件更新防透支。
 */
public interface IBudgetService {

    List<BudgetVo> selectBudgets(BudgetBo query);

    BudgetVo selectBudget(Long budgetId);

    BudgetVo createBudget(BudgetBo bo);

    BudgetVo updateBudget(Long budgetId, BudgetBo bo);

    /** 关闭预算（状态 1 -> 2） */
    void closeBudget(Long budgetId);

    /** 提交冻结；额度不足抛 409 BUDGET_INSUFFICIENT */
    void freeze(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId);

    /** 通过转已用 */
    void settle(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId);

    /** 拒绝/撤销释放冻结 */
    void release(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId);
}
