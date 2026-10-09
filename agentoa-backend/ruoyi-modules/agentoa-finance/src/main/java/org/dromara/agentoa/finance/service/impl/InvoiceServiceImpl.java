package org.dromara.agentoa.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaInvoice;
import org.dromara.agentoa.finance.domain.OaInvoiceAllocation;
import org.dromara.agentoa.finance.mapper.OaInvoiceAllocationMapper;
import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.InvoiceBo;
import org.dromara.agentoa.finance.domain.enums.FinanceEventAction;
import org.dromara.agentoa.finance.domain.enums.InvoiceReservationAction;
import org.dromara.agentoa.finance.domain.policy.FinanceAccessPolicy;
import org.dromara.agentoa.finance.domain.vo.InvoiceVo;
import org.dromara.agentoa.finance.mapper.FinanceIdentityReadMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceMapper;
import org.dromara.agentoa.finance.service.IInvoiceService;
import org.dromara.agentoa.finance.service.support.FinanceLedgerWriter;
import org.dromara.agentoa.finance.service.support.InvoiceRef;
import org.dromara.agentoa.finance.service.support.MoneyUtil;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 发票录入与占用：身份规范化去重，占用/释放/付款全部条件更新 + 事件键幂等。
 */
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements IInvoiceService {

    private final OaInvoiceMapper invoiceMapper;
    private final OaInvoiceAllocationMapper allocationMapper;
    private final FinanceLedgerWriter ledgerWriter;
    private final FinanceIdentityReadMapper identityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVo create(InvoiceBo bo, Long ownerUserId) {
        long amountFen = MoneyUtil.toFen(bo.getAmount());
        if (amountFen <= 0) {
            throw new ServiceException("FINANCE_AMOUNT_INVALID 发票金额必须大于 0", 400);
        }
        OaInvoice invoice = new OaInvoice();
        invoice.setInvoiceType(bo.getInvoiceType().trim());
        invoice.setInvoiceCode(bo.getInvoiceCode() == null ? "" : bo.getInvoiceCode().trim());
        invoice.setInvoiceNo(bo.getInvoiceNo().trim());
        invoice.setInvoiceDate(bo.getInvoiceDate());
        invoice.setAmount(MoneyUtil.toAmount(amountFen));
        invoice.setFingerprint(fingerprint(invoice));
        invoice.setFileId(bo.getFileId());
        invoice.setOwnerUserId(ownerUserId);
        invoice.setLockVersion(0);
        invoice.setCreateTime(new Date());
        invoice.setUpdateTime(new Date());
        try {
            invoiceMapper.insert(invoice);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("FINANCE_INVOICE_DUPLICATE 发票已存在", 409);
        }
        return toVo(invoice);
    }

    @Override
    public InvoiceVo get(Long id, Long actorUserId) {
        OaInvoice invoice = require(id);
        if (!FinanceAccessPolicy.canViewInvoice(roleKeys(actorUserId), actorUserId, invoice.getOwnerUserId())) {
            throw new ServiceException("WF_FORBIDDEN 无权查看该发票", 403);
        }
        return toVo(invoice);
    }

    @Override
    public PageVo<InvoiceVo> list(Long ownerUserId, FinancePageQuery page) {
        LambdaQueryWrapper<OaInvoice> query = new LambdaQueryWrapper<OaInvoice>()
            .eq(OaInvoice::getOwnerUserId, ownerUserId)
            .orderByDesc(OaInvoice::getCreateTime);
        IPage<OaInvoice> result = invoiceMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<InvoiceVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaInvoice invoice = require(id);
        if (!invoice.getOwnerUserId().equals(actorUserId)) {
            throw new ServiceException("WF_FORBIDDEN 只能删除本人发票", 403);
        }
        if (invoice.getOccupiedReimburseId() != null || invoice.getPaidReimburseId() != null) {
            throw new ServiceException("FINANCE_INVOICE_OCCUPIED 发票已被报销占用，不可删除", 409);
        }
        invoiceMapper.deleteById(id);
    }

    @Override
    public void validateForSubmit(Long reimburseId, Long submitterUserId, List<InvoiceRef> refs) {
        java.util.Map<Long, Long> invoiceTotals = new java.util.HashMap<>();
        for (InvoiceRef ref : refs) {
            OaInvoice invoice = require(ref.invoiceId());
            if (!invoice.getOwnerUserId().equals(submitterUserId)) {
                throw new ServiceException("FINANCE_INVOICE_NOT_OWNER 发票不属于提交人", 403);
            }
            if (invoice.getPaidReimburseId() != null) {
                throw new ServiceException("FINANCE_INVOICE_PAID 发票已付款占用，不可重复使用", 409);
            }
            if (invoice.getOccupiedReimburseId() != null && !invoice.getOccupiedReimburseId().equals(reimburseId)) {
                throw new ServiceException("FINANCE_INVOICE_OCCUPIED 发票已被其他报销单占用", 409);
            }
            invoiceTotals.merge(ref.invoiceId(), ref.amountFen(), Long::sum);
        }
        for (var entry : invoiceTotals.entrySet()) {
            OaInvoice invoice = require(entry.getKey());
            long invoiceFen = MoneyUtil.toFen(invoice.getAmount());
            if (entry.getValue() > invoiceFen) {
                throw new ServiceException("FINANCE_INVOICE_AMOUNT_EXCEEDED 明细金额超过发票金额", 400);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void occupyForSubmit(Long reimburseId, int submissionNo, Long operatorUserId, List<InvoiceRef> refs) {
        List<InvoiceRef> sorted = refs.stream()
            .sorted((a, b) -> Long.compare(a.invoiceId(), b.invoiceId()))
            .toList();
        for (InvoiceRef ref : sorted) {
            int rows = invoiceMapper.update(null, new LambdaUpdateWrapper<OaInvoice>()
                .eq(OaInvoice::getId, ref.invoiceId())
                .and(wrapper -> wrapper.isNull(OaInvoice::getOccupiedReimburseId)
                    .or().eq(OaInvoice::getOccupiedReimburseId, reimburseId))
                .isNull(OaInvoice::getPaidReimburseId)
                .set(OaInvoice::getOccupiedReimburseId, reimburseId)
                .setSql("lock_version = lock_version + 1"));
            if (rows == 0) {
                throw new ServiceException("FINANCE_INVOICE_OCCUPIED 发票占用冲突", 409);
            }
            ledgerWriter.insertReservation(ref.invoiceId(), reimburseId,
                InvoiceReservationAction.OCCUPY.name(),
                "reimburse:" + reimburseId + ":" + submissionNo + ":occupy:" + ref.invoiceId(), operatorUserId);
            ledgerWriter.insertAllocation(ref.invoiceId(), reimburseId, null,
                MoneyUtil.toAmount(ref.amountFen()),
                "reimburse:" + reimburseId + ":" + submissionNo + ":alloc:" + ref.invoiceId() + ":" + ref.amountFen(),
                operatorUserId);
        }
        if (!sorted.isEmpty()) {
            long totalFen = sorted.stream().mapToLong(InvoiceRef::amountFen).sum();
            ledgerWriter.insertEvent("reimburse:" + reimburseId + ":" + submissionNo + ":occupied",
                "reimburse", reimburseId, FinanceEventAction.OCCUPY.name(),
                MoneyUtil.toAmount(totalFen), operatorUserId, "提交占用发票");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForClaim(Long reimburseId, int submissionNo, Long operatorUserId) {
        List<OaInvoice> occupied = invoiceMapper.selectList(new LambdaQueryWrapper<OaInvoice>()
            .eq(OaInvoice::getOccupiedReimburseId, reimburseId)
            .isNull(OaInvoice::getPaidReimburseId));
        for (OaInvoice invoice : occupied) {
            int rows = invoiceMapper.update(null, new LambdaUpdateWrapper<OaInvoice>()
                .eq(OaInvoice::getId, invoice.getId())
                .eq(OaInvoice::getOccupiedReimburseId, reimburseId)
                .isNull(OaInvoice::getPaidReimburseId)
                .set(OaInvoice::getOccupiedReimburseId, null)
                .setSql("lock_version = lock_version + 1"));
            if (rows > 0) {
                ledgerWriter.insertReservation(invoice.getId(), reimburseId,
                    InvoiceReservationAction.RELEASE.name(),
                    "reimburse:" + reimburseId + ":" + submissionNo + ":release:" + invoice.getId(), operatorUserId);
            }
        }
        allocationMapper.delete(new LambdaQueryWrapper<OaInvoiceAllocation>()
            .eq(OaInvoiceAllocation::getReimburseId, reimburseId));
        ledgerWriter.insertEvent("reimburse:" + reimburseId + ":" + submissionNo + ":released",
            "reimburse", reimburseId, FinanceEventAction.RELEASE.name(), null, operatorUserId, "拒绝/撤销释放发票");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaidForClaim(Long reimburseId, Long operatorUserId) {
        List<OaInvoice> invoices = invoiceMapper.selectList(new LambdaQueryWrapper<OaInvoice>()
            .and(wrapper -> wrapper.eq(OaInvoice::getOccupiedReimburseId, reimburseId)
                .or().eq(OaInvoice::getPaidReimburseId, reimburseId)));
        for (OaInvoice invoice : invoices) {
            int rows = invoiceMapper.update(null, new LambdaUpdateWrapper<OaInvoice>()
                .eq(OaInvoice::getId, invoice.getId())
                .and(wrapper -> wrapper.isNull(OaInvoice::getPaidReimburseId)
                    .or().eq(OaInvoice::getPaidReimburseId, reimburseId))
                .set(OaInvoice::getPaidReimburseId, reimburseId)
                .set(OaInvoice::getOccupiedReimburseId, null)
                .setSql("lock_version = lock_version + 1"));
            if (rows > 0) {
                ledgerWriter.insertReservation(invoice.getId(), reimburseId,
                    InvoiceReservationAction.PAID.name(),
                    "reimburse:" + reimburseId + ":paid:" + invoice.getId(), operatorUserId);
            }
        }
    }

    // ------------------------------------------------------------ internal

    /** 发票身份指纹：规范化（类型|代码|号码|金额|日期），供审计 */
    static String fingerprint(OaInvoice invoice) {
        String normalized = invoice.getInvoiceType().trim().toUpperCase(java.util.Locale.ROOT) + "|"
            + invoice.getInvoiceCode().trim() + "|"
            + invoice.getInvoiceNo().trim() + "|"
            + MoneyUtil.toFen(invoice.getAmount()) + "|"
            + (invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private OaInvoice require(Long id) {
        OaInvoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new ServiceException("FINANCE_INVOICE_NOT_FOUND 发票不存在", 404);
        }
        return invoice;
    }

    private InvoiceVo toVo(OaInvoice invoice) {
        InvoiceVo vo = new InvoiceVo();
        vo.setId(invoice.getId());
        vo.setInvoiceType(invoice.getInvoiceType());
        vo.setInvoiceCode(invoice.getInvoiceCode());
        vo.setInvoiceNo(invoice.getInvoiceNo());
        vo.setInvoiceDate(invoice.getInvoiceDate());
        vo.setAmount(invoice.getAmount() == null ? null : invoice.getAmount().toPlainString());
        vo.setFingerprint(invoice.getFingerprint());
        vo.setFileId(invoice.getFileId());
        vo.setFileName(invoice.getFileId() == null ? null : identityMapper.selectFileName(invoice.getFileId()));
        vo.setOwnerUserId(invoice.getOwnerUserId());
        vo.setOwnerName(identityMapper.selectNickName(invoice.getOwnerUserId()));
        vo.setOccupiedReimburseId(invoice.getOccupiedReimburseId());
        vo.setPaidReimburseId(invoice.getPaidReimburseId());
        vo.setOccupationStatus(invoice.getPaidReimburseId() != null ? "PAID"
            : invoice.getOccupiedReimburseId() != null ? "OCCUPIED" : "FREE");
        vo.setCreateTime(invoice.getCreateTime() == null ? null : String.valueOf(invoice.getCreateTime()));
        return vo;
    }

    /** 供控制器做角色判定：当前用户的财务/出纳角色键 */
    private Set<String> roleKeys(Long actorUserId) {
        var loginUser = org.dromara.common.satoken.utils.LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getRolePermission() == null ? Set.of() : loginUser.getRolePermission();
    }
}
