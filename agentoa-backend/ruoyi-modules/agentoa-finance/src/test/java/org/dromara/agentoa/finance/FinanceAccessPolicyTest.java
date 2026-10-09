package org.dromara.agentoa.finance;

import org.dromara.agentoa.finance.domain.policy.FinanceAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 4 权限矩阵（docs/14 权限与安全）：管理员不自动获得发票明文与付款登记。
 */
class FinanceAccessPolicyTest {

    private static final Set<String> FINANCE = Set.of("finance");
    private static final Set<String> CASHIER = Set.of("cashier");
    private static final Set<String> MANAGER = Set.of("dept_manager");
    private static final Set<String> EMPLOYEE = Set.of("employee");
    private static final Set<String> NONE = Set.of();

    @Test
    void invoicePlaintextForOwnerAndFinanceRolesOnly() {
        assertThat(FinanceAccessPolicy.canViewInvoice(EMPLOYEE, 200L, 200L)).isTrue();
        assertThat(FinanceAccessPolicy.canViewInvoice(EMPLOYEE, 200L, 300L)).isFalse();
        assertThat(FinanceAccessPolicy.canViewInvoice(MANAGER, 210L, 300L)).isFalse();
        assertThat(FinanceAccessPolicy.canViewInvoice(NONE, 1L, 300L)).isFalse();
        assertThat(FinanceAccessPolicy.canViewInvoice(FINANCE, 500L, 300L)).isTrue();
        assertThat(FinanceAccessPolicy.canViewInvoice(CASHIER, 600L, 300L)).isTrue();
    }

    @Test
    void paymentRegistrationRequiresFinanceOrCashierRole() {
        assertThat(FinanceAccessPolicy.canRegisterPayment(FINANCE)).isTrue();
        assertThat(FinanceAccessPolicy.canRegisterPayment(CASHIER)).isTrue();
        assertThat(FinanceAccessPolicy.canRegisterPayment(MANAGER)).isFalse();
        assertThat(FinanceAccessPolicy.canRegisterPayment(EMPLOYEE)).isFalse();
        assertThat(FinanceAccessPolicy.canRegisterPayment(NONE)).isFalse();
    }

    @Test
    void reportScopeSplitsFinanceAndManager() {
        assertThat(FinanceAccessPolicy.canViewAllDepartments(FINANCE)).isTrue();
        assertThat(FinanceAccessPolicy.canViewAllDepartments(MANAGER)).isFalse();
        assertThat(FinanceAccessPolicy.canViewReports(false, Set.of("fn:report:list"))).isTrue();
        assertThat(FinanceAccessPolicy.canViewReports(false, Set.of())).isFalse();
        assertThat(FinanceAccessPolicy.canViewReports(true, Set.of())).isTrue();
        assertThat(FinanceAccessPolicy.canExport(true, Set.of())).isTrue();
        assertThat(FinanceAccessPolicy.canExport(false, Set.of("fn:report:list"))).isFalse();
        assertThat(FinanceAccessPolicy.canExport(false, Set.of("fn:report:export"))).isTrue();
    }
}
