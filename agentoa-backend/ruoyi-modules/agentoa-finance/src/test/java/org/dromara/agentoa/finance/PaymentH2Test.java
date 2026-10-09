package org.dromara.agentoa.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.finance.domain.OaFinanceEvent;
import org.dromara.agentoa.finance.domain.bo.PaymentBo;
import org.dromara.agentoa.finance.support.FinanceTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 付款登记（docs/14 第 5 步 / docs/05 6.4）：一次全额付款、幂等重放、重复付款拒绝。
 */
class PaymentH2Test {

    @BeforeAll
    static void boot() {
        FinanceTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        FinanceTestEnvironment.clearData();
        FinanceTestEnvironment.logout();
        FinanceTestEnvironment.seedDept(FinanceTestEnvironment.DEPT_A, "测试部门");
        FinanceTestEnvironment.seedUser(FinanceTestEnvironment.USER_EMPLOYEE, FinanceTestEnvironment.DEPT_A, "员工");
        FinanceTestEnvironment.loginAs(FinanceTestEnvironment.USER_CASHIER, FinanceTestEnvironment.DEPT_A,
            java.util.Set.of("fn:payment:add", "fn:payment:list"), java.util.Set.of("cashier"));
    }

    @Test
    void payRegistersOnceAndMarksClaimPaid() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"invoiceId\":1,\"amount\":\"2500.00\"}]", 5);

        var vo = FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"), "pay-key-1",
            FinanceTestEnvironment.USER_CASHIER);

        assertThat(vo.getPaymentNo()).startsWith("PAY");
        assertThat(vo.getAmount()).isEqualTo("2500.00");
        assertThat(vo.getPayStatus()).isEqualTo(2);
        assertThat(FinanceTestEnvironment.claims.selectById(1L).getStatus()).isEqualTo(6);
        assertThat(FinanceTestEnvironment.invoices.selectById(1L).getPaidReimburseId()).isEqualTo(1L);
        long events = FinanceTestEnvironment.financeEvents.selectCount(new LambdaQueryWrapper<OaFinanceEvent>()
            .eq(OaFinanceEvent::getBizId, 1L).eq(OaFinanceEvent::getAction, "PAY"));
        assertThat(events).isEqualTo(1);
        Long outbox = FinanceTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class);
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReplaysFirstPayment() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        var first = FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"), "pay-key-2",
            FinanceTestEnvironment.USER_CASHIER);
        var replay = FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"), "pay-key-2",
            FinanceTestEnvironment.USER_CASHIER);
        assertThat(replay.getId()).isEqualTo(first.getId());
    }

    @Test
    void secondPaymentRejectedWithDifferentKey() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"), "pay-key-3",
            FinanceTestEnvironment.USER_CASHIER);
        assertThatThrownBy(() -> FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"),
            "pay-key-4", FinanceTestEnvironment.USER_CASHIER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_ALREADY_PAID");
    }

    @Test
    void partialPaymentAllowedWithinRemaining() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        var vo = FinanceTestEnvironment.paymentService.pay(1L, paymentBo("1000.00"), "pay-key-5a",
            FinanceTestEnvironment.USER_CASHIER);
        assertThat(vo.getAmount()).isEqualTo("1000.00");
        assertThat(FinanceTestEnvironment.claims.selectById(1L).getStatus()).isEqualTo(5);
        assertThat(FinanceTestEnvironment.claims.selectById(1L).getPaidAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void overpaymentRejected() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        assertThatThrownBy(() -> FinanceTestEnvironment.paymentService.pay(1L, paymentBo("3000.00"),
            "pay-key-5b", FinanceTestEnvironment.USER_CASHIER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_AMOUNT_MISMATCH");
    }

    @Test
    void fullPaymentAfterPartialTransitionsToPaid() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        FinanceTestEnvironment.paymentService.pay(1L, paymentBo("1000.00"), "pay-key-5c",
            FinanceTestEnvironment.USER_CASHIER);
        var vo = FinanceTestEnvironment.paymentService.pay(1L, paymentBo("1500.00"), "pay-key-5d",
            FinanceTestEnvironment.USER_CASHIER);
        assertThat(vo.getAmount()).isEqualTo("1500.00");
        assertThat(FinanceTestEnvironment.claims.selectById(1L).getStatus()).isEqualTo(6);
        assertThat(FinanceTestEnvironment.claims.selectById(1L).getPaidAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void nonPayableStatusRejected() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 2);
        assertThatThrownBy(() -> FinanceTestEnvironment.paymentService.pay(1L, paymentBo("2500.00"),
            "pay-key-6", FinanceTestEnvironment.USER_CASHIER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_STATE_CONFLICT");
    }

    @Test
    void lockVersionConflictRejected() {
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "2500.00",
            "[{\"amount\":\"2500.00\"}]", 5);
        PaymentBo bo = paymentBo("2500.00");
        bo.setLockVersion(99);
        assertThatThrownBy(() -> FinanceTestEnvironment.paymentService.pay(1L, bo,
            "pay-key-7", FinanceTestEnvironment.USER_CASHIER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("VERSION_CONFLICT");
    }

    private static PaymentBo paymentBo(String amount) {
        PaymentBo bo = new PaymentBo();
        bo.setAmount(amount);
        bo.setPayDate(LocalDate.of(2026, 10, 15));
        bo.setPaymentMethod("BANK_TRANSFER");
        bo.setVoucherNo("V-20261015-001");
        return bo;
    }
}
