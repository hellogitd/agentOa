package org.dromara.agentoa.finance.service;

import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.InvoiceBo;
import org.dromara.agentoa.finance.domain.vo.InvoiceVo;
import org.dromara.agentoa.finance.service.support.InvoiceRef;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 发票与占用（docs/05 6.5 / docs/14 第 3 步）：提交时按固定 ID 顺序占用，
 * 拒绝/撤销释放，付款后永久占用；同一发票不得分摊到多条明细或多单。
 */
public interface IInvoiceService {

    InvoiceVo create(InvoiceBo bo, Long ownerUserId);

    InvoiceVo get(Long id, Long actorUserId);

    PageVo<InvoiceVo> list(Long ownerUserId, FinancePageQuery page);

    void delete(Long id, Long actorUserId);

    /** 提交前校验：归属、明细金额、占用状态、同一发票单次引用 */
    void validateForSubmit(Long reimburseId, Long submitterUserId, List<InvoiceRef> refs);

    /** 提交时占用（事件 ID 幂等，重复消费不重复占用） */
    void occupyForSubmit(Long reimburseId, int submissionNo, Long operatorUserId, List<InvoiceRef> refs);

    /** 拒绝/撤销释放占用 */
    void releaseForClaim(Long reimburseId, int submissionNo, Long operatorUserId);

    /** 付款后转永久占用 */
    void markPaidForClaim(Long reimburseId, Long operatorUserId);
}
