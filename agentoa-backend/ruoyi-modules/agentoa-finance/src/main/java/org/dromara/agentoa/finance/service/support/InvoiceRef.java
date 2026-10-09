package org.dromara.agentoa.finance.service.support;

/**
 * 发票引用（明细 → 发票）：amountFen 为明细金额（最小货币单位）。
 */
public record InvoiceRef(Long invoiceId, long amountFen) {
}
