package org.dromara.agentoa.finance.service;

import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.PaymentBo;
import org.dromara.agentoa.finance.domain.vo.PendingClaimVo;
import org.dromara.agentoa.finance.domain.vo.PaymentVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

/**
 * 付款登记（docs/05 6.4）：仅一次人工全额付款；金额等于已审批金额；
 * 重复请求（Idempotency-Key）返回首次结果，业务唯一键阻止二次付款。
 */
public interface IPaymentService {

    PaymentVo pay(Long reimburseId, PaymentBo bo, String idempotencyKey, Long operatorUserId);

    PaymentVo get(Long id);

    PageVo<PaymentVo> list(FinancePageQuery page);

    /** 已通过/待付款的报销单（财务/出纳视角） */
    PageVo<PendingClaimVo> pendingClaims(Integer status, FinancePageQuery page);
}
