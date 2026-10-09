package org.dromara.agentoa.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.finance.domain.OaBudget;
import org.dromara.agentoa.finance.domain.OaBudgetLedger;
import org.dromara.agentoa.workflow.domain.OaReimburseRequest;
import org.dromara.agentoa.finance.domain.bo.BudgetBo;
import org.dromara.agentoa.finance.domain.vo.BudgetVo;
import org.dromara.agentoa.finance.support.FinanceTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 集成测试（FN-03）：预算 CRUD、冻结/结算/释放账本、并发防透支与流程联动。
 */
class FinanceBudgetH2Test {

    @BeforeAll
    static void boot() {
        FinanceTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        FinanceTestEnvironment.clearData();
        FinanceTestEnvironment.logout();
        FinanceTestEnvironment.seedUser(FinanceTestEnvironment.USER_EMPLOYEE, FinanceTestEnvironment.DEPT_A, "emp");
        FinanceTestEnvironment.loginAs(FinanceTestEnvironment.USER_FINANCE, FinanceTestEnvironment.DEPT_A,
            java.util.Set.of("fn:budget:add", "fn:budget:edit", "fn:budget:query", "fn:budget:list"),
            java.util.Set.of());
    }

    private BudgetBo budgetBo(String code, String total) {
        BudgetBo bo = new BudgetBo();
        bo.setBudgetCode(code);
        bo.setBudgetName("dept budget");
        bo.setBudgetType(1);
        bo.setOwnerId(FinanceTestEnvironment.DEPT_A);
        bo.setYear(2026);
        bo.setTotalAmount(new BigDecimal(total));
        return bo;
    }

    @Test
    void createBudgetAndComputeUsage() {
        BudgetVo vo = FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-1", "10000.00"));
        assertThat(vo.getAvailableAmount()).isEqualByComparingTo("10000.00");
        assertThat(vo.getWarn()).isFalse();

        FinanceTestEnvironment.budgetService.freeze(vo.getId(), new BigDecimal("8000.00"),
            "reimburse", 1L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        BudgetVo afterFreeze = FinanceTestEnvironment.budgetService.selectBudget(vo.getId());
        assertThat(afterFreeze.getFrozenAmount()).isEqualByComparingTo("8000.00");
        assertThat(afterFreeze.getAvailableAmount()).isEqualByComparingTo("2000.00");
        assertThat(afterFreeze.getWarn()).isTrue();
    }

    @Test
    void freezeBeyondBudgetRejected() {
        BudgetVo vo = FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-2", "100.00"));
        assertThatThrownBy(() -> FinanceTestEnvironment.budgetService.freeze(vo.getId(),
            new BigDecimal("200.00"), "reimburse", 1L, 1, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("BUDGET_INSUFFICIENT");
    }

    @Test
    void ledgerIsIdempotentAndSettleReleaseMoveAmounts() {
        BudgetVo vo = FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-3", "1000.00"));
        FinanceTestEnvironment.budgetService.freeze(vo.getId(), new BigDecimal("300.00"),
            "reimburse", 7L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        // 重复事件不重复扣减
        FinanceTestEnvironment.budgetService.freeze(vo.getId(), new BigDecimal("300.00"),
            "reimburse", 7L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        assertThat(FinanceTestEnvironment.budgets.selectById(vo.getId()).getFrozenAmount())
            .isEqualByComparingTo("300.00");

        FinanceTestEnvironment.budgetService.settle(vo.getId(), new BigDecimal("300.00"),
            "reimburse", 7L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        BudgetVo settled = FinanceTestEnvironment.budgetService.selectBudget(vo.getId());
        assertThat(settled.getUsedAmount()).isEqualByComparingTo("300.00");
        assertThat(settled.getFrozenAmount()).isEqualByComparingTo("0.00");

        FinanceTestEnvironment.budgetService.freeze(vo.getId(), new BigDecimal("100.00"),
            "reimburse", 8L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        FinanceTestEnvironment.budgetService.release(vo.getId(), new BigDecimal("100.00"),
            "reimburse", 8L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        BudgetVo released = FinanceTestEnvironment.budgetService.selectBudget(vo.getId());
        assertThat(released.getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(released.getUsedAmount()).isEqualByComparingTo("300.00");

        assertThat(FinanceTestEnvironment.budgetLedgers.selectCount(new LambdaQueryWrapper<OaBudgetLedger>()
            .eq(OaBudgetLedger::getBudgetId, vo.getId())))
            .as("FREEZE/SETTLE/FREEZE/RELEASE 四条不可变流水，重复 FREEZE 不重复入账")
            .isEqualTo(4L);
    }

    @Test
    void flowListenerFreezesAndSettlesBudget() {
        BudgetVo vo = FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-4", "500.00"));
        OaReimburseRequest claim = FinanceTestEnvironment.seedClaim(30L, FinanceTestEnvironment.USER_EMPLOYEE,
            "300.00", "[{\"expenseTypeId\":1,\"expenseType\":\"office\",\"occurDate\":\"2026-10-01\","
            + "\"amount\":\"300.00\",\"description\":\"office\"}]", 3);
        claim.setBudgetId(vo.getId());
        FinanceTestEnvironment.claims.updateById(claim);

        FinanceTestEnvironment.flowListener.onBusinessStarted(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.REIMBURSE, 30L, 1L, 1);
        assertThat(FinanceTestEnvironment.budgets.selectById(vo.getId()).getFrozenAmount())
            .isEqualByComparingTo("300.00");

        FinanceTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.REIMBURSE, 30L, 1L,
            FinanceTestEnvironment.USER_FINANCE);
        BudgetVo after = FinanceTestEnvironment.budgetService.selectBudget(vo.getId());
        assertThat(after.getUsedAmount()).isEqualByComparingTo("300.00");
        assertThat(after.getFrozenAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void closeBudgetBlockedWhileFrozen() {
        BudgetVo vo = FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-5", "500.00"));
        FinanceTestEnvironment.budgetService.freeze(vo.getId(), new BigDecimal("100.00"),
            "reimburse", 9L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        assertThatThrownBy(() -> FinanceTestEnvironment.budgetService.closeBudget(vo.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能关闭");
    }

    @Test
    void duplicateBudgetCodeRejected() {
        FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-6", "10.00"));
        assertThatThrownBy(() -> FinanceTestEnvironment.budgetService.createBudget(budgetBo("BUD-6", "20.00")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已存在");
    }
}
