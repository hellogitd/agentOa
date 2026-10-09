package org.dromara.agentoa.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaPaymentRecord;
import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.PaymentBo;
import org.dromara.agentoa.finance.domain.enums.FinanceEventAction;
import org.dromara.agentoa.finance.domain.vo.PendingClaimVo;
import org.dromara.agentoa.finance.domain.vo.PaymentVo;
import org.dromara.agentoa.finance.mapper.FinanceIdentityReadMapper;
import org.dromara.agentoa.finance.mapper.OaPaymentRecordMapper;
import org.dromara.agentoa.finance.service.IInvoiceService;
import org.dromara.agentoa.finance.service.IPaymentService;
import org.dromara.agentoa.finance.service.support.FinanceLedgerWriter;
import org.dromara.agentoa.finance.service.support.MoneyUtil;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.workflow.domain.OaReimburseRequest;
import org.dromara.agentoa.workflow.mapper.OaReimburseRequestMapper;
import org.dromara.agentoa.workflow.service.support.OutboxWriter;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 付款登记实现（P1 批次二 FN-05）：支持部分付款，条件更新累计 paid_amount 防超付。
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    static final String PAY_PATH = "POST /api/v1/finance/reimburses/{id}/pay";

    private final OaPaymentRecordMapper paymentMapper;
    private final OaReimburseRequestMapper requestMapper;
    private final IInvoiceService invoiceService;
    private final FinanceLedgerWriter ledgerWriter;
    private final FinanceIdentityReadMapper identityMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVo pay(Long reimburseId, PaymentBo bo, String idempotencyKey, Long operatorUserId) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key == null || key.isEmpty()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        String replayRef = idempotencyGuard.begin(operatorUserId, key, PAY_PATH,
            IdempotencyGuard.digest(JsonUtils.toJsonString(bo)));
        if (replayRef != null) {
            return toVo(requirePayment(Long.valueOf(replayRef)));
        }
        OaReimburseRequest claim = requireClaim(reimburseId);
        int status = claim.getStatus() == null ? 0 : claim.getStatus();
        if (status == 6) {
            throw new ServiceException("FINANCE_ALREADY_PAID 报销单已付款", 409);
        }
        if (status != 3 && status != 5) {
            throw new ServiceException("FINANCE_STATE_CONFLICT 报销单当前状态不可付款", 409);
        }
        if (bo.getLockVersion() != null && !bo.getLockVersion().equals(claim.getLockVersion())) {
            throw new ServiceException("VERSION_CONFLICT 业务版本已变更", 409);
        }
        long totalFen = MoneyUtil.toFen(claim.getTotalAmount());
        long alreadyPaidFen = claim.getPaidAmount() == null ? 0 : MoneyUtil.toFen(claim.getPaidAmount());
        long remainingFen = totalFen - alreadyPaidFen;
        long paidFen = MoneyUtil.toFen(bo.getAmount());
        if (paidFen <= 0) {
            throw new ServiceException("FINANCE_AMOUNT_INVALID 付款金额必须大于 0", 400);
        }
        if (paidFen > remainingFen) {
            throw new ServiceException("FINANCE_AMOUNT_MISMATCH 付款金额超过剩余应付金额", 400);
        }
        OaPaymentRecord record = new OaPaymentRecord();
        record.setPaymentNo(generateNo());
        record.setReimburseId(reimburseId);
        record.setAmount(MoneyUtil.toAmount(paidFen));
        record.setPayDate(bo.getPayDate());
        record.setPaymentMethod(bo.getPaymentMethod());
        record.setVoucherNo(bo.getVoucherNo());
        record.setPayStatus(2);
        record.setOperatorId(operatorUserId);
        record.setCreateTime(new Date());
        record.setUpdateTime(new Date());
        record.setRemark(bo.getRemark());
        paymentMapper.insert(record);
        boolean fullyPaid = (alreadyPaidFen + paidFen) >= totalFen;
        int rows;
        if (fullyPaid) {
            rows = requestMapper.update(null, new LambdaUpdateWrapper<OaReimburseRequest>()
                .eq(OaReimburseRequest::getId, reimburseId)
                .in(OaReimburseRequest::getStatus, 3, 5)
                .set(OaReimburseRequest::getStatus, 6)
                .set(OaReimburseRequest::getPaidAmount, MoneyUtil.toAmount(alreadyPaidFen + paidFen))
                .setSql("lock_version = lock_version + 1"));
        } else {
            rows = requestMapper.update(null, new LambdaUpdateWrapper<OaReimburseRequest>()
                .eq(OaReimburseRequest::getId, reimburseId)
                .in(OaReimburseRequest::getStatus, 3, 5)
                .setSql("paid_amount = paid_amount + " + MoneyUtil.toAmount(paidFen).toPlainString())
                .setSql("lock_version = lock_version + 1"));
        }
        if (rows == 0) {
            throw new ServiceException("FINANCE_STATE_CONFLICT 报销单付款状态冲突", 409);
        }
        if (fullyPaid) {
            invoiceService.markPaidForClaim(reimburseId, operatorUserId);
        }
        long newRemainingFen = totalFen - alreadyPaidFen - paidFen;
        ledgerWriter.insertEvent("reimburse:" + reimburseId + ":pay:" + record.getId(),
            "reimburse", reimburseId, FinanceEventAction.PAY.name(), MoneyUtil.toAmount(paidFen),
            operatorUserId, "登记付款 " + record.getPaymentNo());
        String notifyMsg = fullyPaid
            ? "报销单 " + claim.getReimburseNo() + " 已付清 " + MoneyUtil.toAmountString(paidFen) + " 元"
            : "报销单 " + claim.getReimburseNo() + " 已付 " + MoneyUtil.toAmountString(paidFen)
                + " 元，剩余 " + MoneyUtil.toAmountString(newRemainingFen) + " 元";
        outboxWriter.writeTodo("FPAY-" + reimburseId + "-" + record.getId() + "-" + claim.getUserId(),
            claim.getUserId(), "报销付款通知", notifyMsg,
            "reimburse", reimburseId, "/finance/reimburse");
        idempotencyGuard.complete(operatorUserId, key, String.valueOf(record.getId()));
        return toVo(record);
    }

    @Override
    public PaymentVo get(Long id) {
        return toVo(requirePayment(id));
    }

    @Override
    public PageVo<PaymentVo> list(FinancePageQuery page) {
        IPage<OaPaymentRecord> result = paymentMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaPaymentRecord>().orderByDesc(OaPaymentRecord::getCreateTime));
        List<PaymentVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public PageVo<PendingClaimVo> pendingClaims(Integer status, FinancePageQuery page) {
        LambdaQueryWrapper<OaReimburseRequest> query = new LambdaQueryWrapper<>();
        if (status != null) {
            query.eq(OaReimburseRequest::getStatus, status);
        } else {
            query.in(OaReimburseRequest::getStatus, 3, 5);
        }
        query.orderByDesc(OaReimburseRequest::getCreateTime);
        IPage<OaReimburseRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<PendingClaimVo> records = result.getRecords().stream().map(this::toPendingVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    // ------------------------------------------------------------ internal

    private OaReimburseRequest requireClaim(Long reimburseId) {
        OaReimburseRequest claim = requestMapper.selectById(reimburseId);
        if (claim == null) {
            throw new ServiceException("WF_NOT_FOUND 报销单不存在", 404);
        }
        return claim;
    }

    private OaPaymentRecord requirePayment(Long id) {
        OaPaymentRecord record = paymentMapper.selectById(id);
        if (record == null) {
            throw new ServiceException("FINANCE_PAYMENT_NOT_FOUND 付款记录不存在", 404);
        }
        return record;
    }

    private String generateNo() {
        return "PAY" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private PaymentVo toVo(OaPaymentRecord record) {
        PaymentVo vo = new PaymentVo();
        vo.setId(record.getId());
        vo.setPaymentNo(record.getPaymentNo());
        vo.setReimburseId(record.getReimburseId());
        OaReimburseRequest claim = requestMapper.selectById(record.getReimburseId());
        if (claim != null) {
            vo.setReimburseNo(claim.getReimburseNo());
            vo.setApplicantName(identityMapper.selectNickName(claim.getUserId()));
        }
        vo.setAmount(record.getAmount() == null ? null : record.getAmount().toPlainString());
        vo.setPayDate(record.getPayDate() == null ? null : record.getPayDate().toString());
        vo.setPaymentMethod(record.getPaymentMethod());
        vo.setVoucherNo(record.getVoucherNo());
        vo.setPayStatus(record.getPayStatus());
        vo.setOperatorId(record.getOperatorId());
        vo.setOperatorName(identityMapper.selectNickName(record.getOperatorId()));
        vo.setCreateTime(record.getCreateTime() == null ? null : String.valueOf(record.getCreateTime()));
        vo.setRemark(record.getRemark());
        return vo;
    }

    private PendingClaimVo toPendingVo(OaReimburseRequest claim) {
        PendingClaimVo vo = new PendingClaimVo();
        vo.setId(claim.getId());
        vo.setReimburseNo(claim.getReimburseNo());
        vo.setUserId(claim.getUserId());
        vo.setApplicantName(identityMapper.selectNickName(claim.getUserId()));
        vo.setDeptId(identityMapper.selectUserDeptId(claim.getUserId()));
        vo.setDeptName(vo.getDeptId() == null ? null : identityMapper.selectDeptName(vo.getDeptId()));
        vo.setTotalAmount(claim.getTotalAmount() == null ? "0.00" : claim.getTotalAmount().toPlainString());
        vo.setPaidAmount(claim.getPaidAmount() == null ? "0.00" : claim.getPaidAmount().toPlainString());
        vo.setStatus(claim.getStatus());
        vo.setCreateTime(claim.getCreateTime() == null ? null : String.valueOf(claim.getCreateTime()));
        vo.setUpdateTime(claim.getUpdateTime() == null ? null : String.valueOf(claim.getUpdateTime()));
        return vo;
    }
}
