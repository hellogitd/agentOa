package org.dromara.agentoa.finance.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaExpenseItem;
import org.dromara.agentoa.finance.domain.OaInvoice;
import org.dromara.agentoa.finance.domain.enums.FinanceEventAction;
import org.dromara.agentoa.finance.mapper.FinanceIdentityReadMapper;
import org.dromara.agentoa.finance.mapper.OaExpenseItemMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceMapper;
import org.dromara.agentoa.finance.service.IBudgetService;
import org.dromara.agentoa.finance.service.IInvoiceService;
import org.dromara.agentoa.workflow.domain.OaReimburseRequest;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.mapper.OaReimburseRequestMapper;
import org.dromara.agentoa.workflow.service.support.FlowEventListenerRegistry;
import org.dromara.agentoa.workflow.spi.FlowEventListener;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 财务回调承接（docs/14 第 3/4 步）：提交时校验并占用发票、固化明细，通过转待付款，
 * 拒绝/撤销释放占用。账本动作全部在流程编排事务内执行，失败整体回滚。
 */
@Component
@RequiredArgsConstructor
public class FinanceFlowListener implements FlowEventListener {

    private final FlowEventListenerRegistry listenerRegistry;
    private final OaReimburseRequestMapper requestMapper;
    private final OaExpenseItemMapper expenseItemMapper;
    private final OaInvoiceMapper invoiceMapper;
    private final IInvoiceService invoiceService;
    private final FinanceLedgerWriter ledgerWriter;
    private final FinanceIdentityReadMapper identityMapper;
    private final IBudgetService budgetService;

    @PostConstruct
    public void register() {
        listenerRegistry.register(this);
    }

    @Override
    public void onBusinessValidated(BusinessType type, Long businessId, Long submitterUserId) {
        if (type != BusinessType.REIMBURSE) {
            return;
        }
        OaReimburseRequest claim = requireClaim(businessId);
        List<ClaimDetailParser.ClaimDetail> details = parseDetails(claim);
        validateExpenseTypes(details);
        invoiceService.validateForSubmit(businessId, claim.getUserId(), resolveRefs(claim, details));
    }

    @Override
    public void onBusinessStarted(BusinessType type, Long businessId, Long instanceId, int submissionNo) {
        if (type != BusinessType.REIMBURSE) {
            return;
        }
        OaReimburseRequest claim = requireClaim(businessId);
        List<ClaimDetailParser.ClaimDetail> details = parseDetails(claim);
        snapshotItems(claim, details, submissionNo);
        invoiceService.occupyForSubmit(businessId, submissionNo, claim.getUserId(), resolveRefs(claim, details));
        // P1 预算（FN-03）：提交冻结，拒绝/撤销释放，通过转已用
        if (claim.getBudgetId() != null && claim.getTotalAmount() != null) {
            budgetService.freeze(claim.getBudgetId(), claim.getTotalAmount(), "reimburse", businessId,
                submissionNo, claim.getUserId());
        }
    }

    @Override
    public void onBusinessApproved(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        if (type != BusinessType.REIMBURSE) {
            return;
        }
        OaReimburseRequest claim = requireClaim(businessId);
        int rows = requestMapper.update(null, new LambdaUpdateWrapper<OaReimburseRequest>()
            .eq(OaReimburseRequest::getId, businessId)
            .eq(OaReimburseRequest::getStatus, 3)
            .set(OaReimburseRequest::getStatus, 5)
            .setSql("lock_version = lock_version + 1"));
        if (rows > 0) {
            ledgerWriter.insertEvent("reimburse:" + businessId + ":approve",
                "reimburse", businessId, FinanceEventAction.APPROVE.name(),
                claim.getTotalAmount(), operatorUserId, "����ͨ��ת������");
            if (claim.getBudgetId() != null && claim.getTotalAmount() != null) {
                budgetService.settle(claim.getBudgetId(), claim.getTotalAmount(), "reimburse", businessId,
                    claim.getSubmissionNo() == null ? 0 : claim.getSubmissionNo(), operatorUserId);
            }
        }
    }

    @Override
    public void onBusinessRejected(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        release(type, businessId, operatorUserId);
    }

    @Override
    public void onBusinessRevoked(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        release(type, businessId, operatorUserId);
    }

    // ------------------------------------------------------------ internal

    private void release(BusinessType type, Long businessId, Long operatorUserId) {
        if (type != BusinessType.REIMBURSE) {
            return;
        }
        OaReimburseRequest claim = requireClaim(businessId);
        int submissionNo = claim.getSubmissionNo() == null ? 0 : claim.getSubmissionNo();
        invoiceService.releaseForClaim(businessId, submissionNo, operatorUserId == null ? claim.getUserId() : operatorUserId);
        if (claim.getBudgetId() != null && claim.getTotalAmount() != null) {
            budgetService.release(claim.getBudgetId(), claim.getTotalAmount(), "reimburse", businessId,
                submissionNo, operatorUserId == null ? claim.getUserId() : operatorUserId);
        }
    }

    private void snapshotItems(OaReimburseRequest claim, List<ClaimDetailParser.ClaimDetail> details, int submissionNo) {
        expenseItemMapper.delete(new LambdaQueryWrapper<OaExpenseItem>()
            .eq(OaExpenseItem::getReimburseId, claim.getId()));
        for (ClaimDetailParser.ClaimDetail detail : details) {
            OaExpenseItem item = new OaExpenseItem();
            item.setReimburseId(claim.getId());
            item.setSubmissionNo(submissionNo);
            item.setInvoiceId(resolveInvoiceId(claim, detail));
            item.setExpenseTypeId(detail.expenseTypeId());
            item.setExpenseType(detail.expenseType() != null ? detail.expenseType()
                : detail.expenseTypeId() == null ? null : identityMapper.selectExpenseTypeCode(detail.expenseTypeId()));
            item.setOccurDate(detail.occurDate());
            item.setAmount(org.dromara.agentoa.finance.service.support.MoneyUtil.toAmount(detail.amountFen()));
            item.setDescription(detail.description());
            item.setSort(detail.sort());
            item.setCreateTime(new Date());
            expenseItemMapper.insert(item);
        }
    }

    private List<InvoiceRef> resolveRefs(OaReimburseRequest claim, List<ClaimDetailParser.ClaimDetail> details) {
        List<InvoiceRef> refs = new ArrayList<>();
        for (ClaimDetailParser.ClaimDetail detail : details) {
            Long invoiceId = resolveInvoiceId(claim, detail);
            if (invoiceId != null) {
                refs.add(new InvoiceRef(invoiceId, detail.amountFen()));
            }
        }
        return refs;
    }

    /** invoiceId 优先；模块 2 草案的 invoiceNo 字段在本人发票中唯一解析 */
    private Long resolveInvoiceId(OaReimburseRequest claim, ClaimDetailParser.ClaimDetail detail) {
        if (detail.invoiceId() != null) {
            return detail.invoiceId();
        }
        if (detail.invoiceNo() == null || detail.invoiceNo().isBlank()) {
            return null;
        }
        List<OaInvoice> matches = invoiceMapper.selectList(new LambdaQueryWrapper<OaInvoice>()
            .eq(OaInvoice::getOwnerUserId, claim.getUserId())
            .eq(OaInvoice::getInvoiceNo, detail.invoiceNo().trim())
            .last("LIMIT 2"));
        if (matches.isEmpty()) {
            throw new ServiceException("FINANCE_INVOICE_NOT_FOUND 发票不存在: " + detail.invoiceNo(), 404);
        }
        if (matches.size() > 1) {
            throw new ServiceException("FINANCE_INVOICE_AMBIGUOUS 发票号码不唯一，请改用 invoiceId", 400);
        }
        return matches.get(0).getId();
    }

    private void validateExpenseTypes(List<ClaimDetailParser.ClaimDetail> details) {
        for (ClaimDetailParser.ClaimDetail detail : details) {
            if (detail.expenseTypeId() != null && identityMapper.selectExpenseTypeCode(detail.expenseTypeId()) == null) {
                throw new ServiceException("FINANCE_EXPENSE_TYPE_NOT_FOUND 费用类型不存在: " + detail.expenseTypeId(), 404);
            }
        }
    }

    private List<ClaimDetailParser.ClaimDetail> parseDetails(OaReimburseRequest claim) {
        List<ClaimDetailParser.ClaimDetail> details = ClaimDetailParser.parse(claim.getDetailsJson());
        if (details.isEmpty()) {
            throw new ServiceException("报销明细不能为空", 400);
        }
        return details;
    }

    private OaReimburseRequest requireClaim(Long businessId) {
        OaReimburseRequest claim = requestMapper.selectById(businessId);
        if (claim == null) {
            throw new ServiceException("WF_NOT_FOUND 报销单不存在", 404);
        }
        return claim;
    }
}
