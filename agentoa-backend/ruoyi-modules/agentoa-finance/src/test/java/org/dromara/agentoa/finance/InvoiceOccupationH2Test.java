package org.dromara.agentoa.finance;

import org.dromara.agentoa.finance.domain.OaExpenseItem;
import org.dromara.agentoa.finance.domain.OaInvoice;
import org.dromara.agentoa.finance.domain.OaInvoiceReservation;
import org.dromara.agentoa.finance.service.support.FinanceFlowListener;
import org.dromara.agentoa.finance.service.support.InvoiceRef;
import org.dromara.agentoa.finance.support.FinanceTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发票占用（docs/14 第 3 步）：提交占用、拒绝/撤销释放、付款后永久占用，并发只有一个在途单。
 */
class InvoiceOccupationH2Test {

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
        FinanceTestEnvironment.seedUser(FinanceTestEnvironment.USER_OTHER, FinanceTestEnvironment.DEPT_A, "他人");
        FinanceTestEnvironment.seedExpenseType(1L, "office", "办公费");
    }

    @Test
    void submitOccupiesInvoiceAndSnapshotsItems() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", null, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"expenseTypeId\":1,\"expenseType\":\"office\",\"occurDate\":\"2026-10-01\","
                + "\"amount\":\"500.00\",\"description\":\"办公用品\"}]", 2);

        listener().onBusinessValidated(reimburse(), 1L, FinanceTestEnvironment.USER_EMPLOYEE);
        listener().onBusinessStarted(reimburse(), 1L, 1001L, 1);

        OaInvoice invoice = FinanceTestEnvironment.invoices.selectById(1L);
        assertThat(invoice.getOccupiedReimburseId()).isEqualTo(1L);
        assertThat(invoice.getPaidReimburseId()).isNull();

        List<OaExpenseItem> items = FinanceTestEnvironment.expenseItems.selectList(
            new LambdaQueryWrapper<OaExpenseItem>().eq(OaExpenseItem::getReimburseId, 1L));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getInvoiceId()).isEqualTo(1L);
        assertThat(items.get(0).getAmount()).isEqualByComparingTo("500.00");
        assertThat(items.get(0).getExpenseType()).isEqualTo("office");

        long reservations = FinanceTestEnvironment.reservations.selectCount(
            new LambdaQueryWrapper<OaInvoiceReservation>()
                .eq(OaInvoiceReservation::getReimburseId, 1L)
                .eq(OaInvoiceReservation::getAction, "OCCUPY"));
        assertThat(reservations).isEqualTo(1);
    }

    @Test
    void secondClaimCannotTakeOccupiedInvoice() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(2L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"amount\":\"500.00\"}]", 2);

        assertThatThrownBy(() -> listener().onBusinessValidated(reimburse(), 2L, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_OCCUPIED");
    }

    @Test
    void concurrentOccupationOnlyOneWins() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", null, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);
        FinanceTestEnvironment.seedClaim(2L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);
        List<InvoiceRef> refs = List.of(new InvoiceRef(1L, 50000L));

        FinanceTestEnvironment.invoiceService.occupyForSubmit(1L, 1, FinanceTestEnvironment.USER_EMPLOYEE, refs);
        assertThatThrownBy(() -> FinanceTestEnvironment.invoiceService.occupyForSubmit(2L, 1,
            FinanceTestEnvironment.USER_EMPLOYEE, refs))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_OCCUPIED");
    }

    @Test
    void rejectReleasesInvoiceAndAllowsReuse() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);

        listener().onBusinessRejected(reimburse(), 1L, 1001L, FinanceTestEnvironment.USER_EMPLOYEE);

        OaInvoice invoice = FinanceTestEnvironment.invoices.selectById(1L);
        assertThat(invoice.getOccupiedReimburseId()).isNull();
        long releases = FinanceTestEnvironment.reservations.selectCount(
            new LambdaQueryWrapper<OaInvoiceReservation>()
                .eq(OaInvoiceReservation::getReimburseId, 1L)
                .eq(OaInvoiceReservation::getAction, "RELEASE"));
        assertThat(releases).isEqualTo(1);

        // 释放后其他报销单可以占用
        FinanceTestEnvironment.seedClaim(2L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);
        FinanceTestEnvironment.invoiceService.occupyForSubmit(2L, 1, FinanceTestEnvironment.USER_EMPLOYEE,
            List.of(new InvoiceRef(1L, 50000L)));
        assertThat(FinanceTestEnvironment.invoices.selectById(1L).getOccupiedReimburseId()).isEqualTo(2L);
    }

    @Test
    void revokeReleasesInvoice() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);

        listener().onBusinessRevoked(reimburse(), 1L, 1001L, FinanceTestEnvironment.USER_EMPLOYEE);

        assertThat(FinanceTestEnvironment.invoices.selectById(1L).getOccupiedReimburseId()).isNull();
    }

    @Test
    void paidInvoiceStaysOccupiedForever() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00", "[]", 2);

        FinanceTestEnvironment.invoiceService.markPaidForClaim(1L, FinanceTestEnvironment.USER_CASHIER);
        OaInvoice paid = FinanceTestEnvironment.invoices.selectById(1L);
        assertThat(paid.getPaidReimburseId()).isEqualTo(1L);

        // 付款后释放不会归还发票，其他报销单永久不可用
        FinanceTestEnvironment.invoiceService.releaseForClaim(1L, 1, FinanceTestEnvironment.USER_EMPLOYEE);
        assertThat(FinanceTestEnvironment.invoices.selectById(1L).getPaidReimburseId()).isEqualTo(1L);

        FinanceTestEnvironment.seedClaim(2L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"amount\":\"500.00\"}]", 2);
        assertThatThrownBy(() -> listener().onBusinessValidated(reimburse(), 2L, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_PAID");
    }

    @Test
    void nonOwnerAndOverAmountAndDuplicateRefsAreRejected() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_OTHER, "INV-1", "1000.00", null, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"amount\":\"500.00\"}]", 2);
        assertThatThrownBy(() -> listener().onBusinessValidated(reimburse(), 1L, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_NOT_OWNER");

        FinanceTestEnvironment.invoices.deleteById(1L);
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", null, null);
        FinanceTestEnvironment.seedClaim(2L, FinanceTestEnvironment.USER_EMPLOYEE, "5000.00",
            "[{\"invoiceId\":1,\"amount\":\"5000.00\"}]", 2);
        assertThatThrownBy(() -> listener().onBusinessValidated(reimburse(), 2L, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_AMOUNT_EXCEEDED");

        FinanceTestEnvironment.seedClaim(3L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"amount\":\"200.00\"},{\"invoiceId\":1,\"amount\":\"300.00\"}]", 2);
        listener().onBusinessValidated(reimburse(), 3L, FinanceTestEnvironment.USER_EMPLOYEE);

        FinanceTestEnvironment.seedClaim(4L, FinanceTestEnvironment.USER_EMPLOYEE, "1500.00",
            "[{\"invoiceId\":1,\"amount\":\"600.00\"},{\"invoiceId\":1,\"amount\":\"900.00\"}]", 2);
        assertThatThrownBy(() -> listener().onBusinessValidated(reimburse(), 4L, FinanceTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_INVOICE_AMOUNT_EXCEEDED");
    }

    @Test
    void approveMovesClaimToPendingPayment() {
        FinanceTestEnvironment.seedInvoice(1L, FinanceTestEnvironment.USER_EMPLOYEE, "INV-1", "1000.00", 1L, null);
        FinanceTestEnvironment.seedClaim(1L, FinanceTestEnvironment.USER_EMPLOYEE, "500.00",
            "[{\"invoiceId\":1,\"amount\":\"500.00\"}]", 3);

        listener().onBusinessApproved(reimburse(), 1L, 1001L, FinanceTestEnvironment.USER_FINANCE);

        var claim = FinanceTestEnvironment.claims.selectById(1L);
        assertThat(claim.getStatus()).isEqualTo(5);
        // 审批通过保持占用
        assertThat(FinanceTestEnvironment.invoices.selectById(1L).getOccupiedReimburseId()).isEqualTo(1L);
    }

    private static org.dromara.agentoa.workflow.domain.enums.BusinessType reimburse() {
        return org.dromara.agentoa.workflow.domain.enums.BusinessType.REIMBURSE;
    }

    private static FinanceFlowListener listener() {
        return FinanceTestEnvironment.flowListener;
    }
}
