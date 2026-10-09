package org.dromara.agentoa.finance.domain.enums;

/**
 * 发票占用动作（docs/14 第 3 步，字典 fn_finance_action）。
 */
public enum InvoiceReservationAction {

    /** 提交时占用 */
    OCCUPY,
    /** 拒绝/撤销释放 */
    RELEASE,
    /** 付款后永久占用 */
    PAID
}
