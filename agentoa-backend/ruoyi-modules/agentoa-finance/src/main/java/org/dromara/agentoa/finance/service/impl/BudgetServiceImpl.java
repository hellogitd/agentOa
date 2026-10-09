package org.dromara.agentoa.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaBudget;
import org.dromara.agentoa.finance.domain.OaBudgetLedger;
import org.dromara.agentoa.finance.domain.bo.BudgetBo;
import org.dromara.agentoa.finance.domain.vo.BudgetVo;
import org.dromara.agentoa.finance.mapper.OaBudgetLedgerMapper;
import org.dromara.agentoa.finance.mapper.OaBudgetMapper;
import org.dromara.agentoa.finance.service.IBudgetService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 预算服务实现（P1，FN-03）。对齐请假额度账本口径：
 * 不可变流水 + (budget_id,event_key) 唯一键幂等 + 条件更新防并发透支。
 */
@RequiredArgsConstructor
@Service
public class BudgetServiceImpl implements IBudgetService {

    private final OaBudgetMapper budgetMapper;
    private final OaBudgetLedgerMapper ledgerMapper;

    @Override
    public List<BudgetVo> selectBudgets(BudgetBo query) {
        LambdaQueryWrapper<OaBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getBudgetType() != null, OaBudget::getBudgetType, query.getBudgetType())
            .eq(query.getOwnerId() != null, OaBudget::getOwnerId, query.getOwnerId())
            .eq(query.getYear() != null, OaBudget::getYear, query.getYear())
            .eq(query.getQuarter() != null, OaBudget::getQuarter, query.getQuarter())
            .like(StringUtils.isNotBlank(query.getBudgetCode()), OaBudget::getBudgetCode, query.getBudgetCode())
            .eq(query.getStatus() != null, OaBudget::getStatus, query.getStatus())
            .orderByDesc(OaBudget::getYear)
            .orderByAsc(OaBudget::getBudgetCode);
        return budgetMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    public BudgetVo selectBudget(Long budgetId) {
        return toVo(requireBudget(budgetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetVo createBudget(BudgetBo bo) {
        String code = StringUtils.isBlank(bo.getBudgetCode()) ? generateCode(bo.getYear()) : bo.getBudgetCode().trim();
        if (budgetMapper.exists(new LambdaQueryWrapper<OaBudget>().eq(OaBudget::getBudgetCode, code))) {
            throw new ServiceException("预算编号'" + code + "'已存在", 409);
        }
        OaBudget budget = new OaBudget();
        budget.setBudgetCode(code);
        budget.setBudgetName(bo.getBudgetName());
        budget.setBudgetType(bo.getBudgetType());
        budget.setOwnerId(bo.getOwnerId());
        budget.setYear(bo.getYear());
        budget.setQuarter(bo.getQuarter());
        budget.setTotalAmount(bo.getTotalAmount());
        budget.setUsedAmount(BigDecimal.ZERO.setScale(2));
        budget.setFrozenAmount(BigDecimal.ZERO.setScale(2));
        budget.setWarnThreshold(bo.getWarnThreshold() == null ? 80 : bo.getWarnThreshold());
        budget.setStatus(OaBudget.STATUS_ACTIVE);
        budget.setLockVersion(0);
        budget.setRemark(bo.getRemark());
        budgetMapper.insert(budget);
        return toVo(budget);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetVo updateBudget(Long budgetId, BudgetBo bo) {
        OaBudget existing = requireBudget(budgetId);
        if (existing.getStatus() == OaBudget.STATUS_CLOSED) {
            throw new ServiceException("已关闭预算不能修改", 409);
        }
        if (StringUtils.isNotBlank(bo.getBudgetCode()) && !bo.getBudgetCode().equals(existing.getBudgetCode())
            && budgetMapper.exists(new LambdaQueryWrapper<OaBudget>().eq(OaBudget::getBudgetCode, bo.getBudgetCode()))) {
            throw new ServiceException("预算编号'" + bo.getBudgetCode() + "'已存在", 409);
        }
        OaBudget budget = new OaBudget();
        budget.setId(budgetId);
        budget.setBudgetCode(StringUtils.isBlank(bo.getBudgetCode()) ? null : bo.getBudgetCode().trim());
        budget.setBudgetName(bo.getBudgetName());
        budget.setBudgetType(bo.getBudgetType());
        budget.setOwnerId(bo.getOwnerId());
        budget.setYear(bo.getYear());
        budget.setQuarter(bo.getQuarter());
        budget.setTotalAmount(bo.getTotalAmount());
        budget.setWarnThreshold(bo.getWarnThreshold());
        budget.setRemark(bo.getRemark());
        budgetMapper.updateById(budget);
        return toVo(requireBudget(budgetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeBudget(Long budgetId) {
        OaBudget budget = requireBudget(budgetId);
        if (budget.getStatus() == OaBudget.STATUS_CLOSED) {
            return;
        }
        if (budget.getFrozenAmount() != null && budget.getFrozenAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException("存在未结算冻结金额，不能关闭", 409);
        }
        OaBudget update = new OaBudget();
        update.setId(budgetId);
        update.setStatus(OaBudget.STATUS_CLOSED);
        budgetMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId) {
        requireBudget(budgetId);
        String eventKey = "FREEZE:" + bizType + ":" + bizId + ":" + submissionNo;
        if (!insertLedger(budgetId, eventKey, OaBudgetLedger.ACTION_OCCUPY, amount, bizType, bizId, operatorUserId)) {
            return;
        }
        int rows = budgetMapper.update(null, new LambdaUpdateWrapper<OaBudget>()
            .eq(OaBudget::getId, budgetId)
            .eq(OaBudget::getStatus, OaBudget.STATUS_ACTIVE)
            .apply("total_amount >= used_amount + frozen_amount + {0}", amount)
            .setSql("frozen_amount = frozen_amount + " + literal(amount))
            .setSql("lock_version = lock_version + 1"));
        if (rows == 0) {
            throw new ServiceException("BUDGET_INSUFFICIENT 预算额度不足", 409);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId) {
        requireBudget(budgetId);
        String eventKey = "SETTLE:" + bizType + ":" + bizId + ":" + submissionNo;
        if (!insertLedger(budgetId, eventKey, OaBudgetLedger.ACTION_SETTLE, amount, bizType, bizId, operatorUserId)) {
            return;
        }
        int rows = budgetMapper.update(null, new LambdaUpdateWrapper<OaBudget>()
            .eq(OaBudget::getId, budgetId)
            .apply("frozen_amount >= {0}", amount)
            .setSql("frozen_amount = frozen_amount - " + literal(amount))
            .setSql("used_amount = used_amount + " + literal(amount))
            .setSql("lock_version = lock_version + 1"));
        if (rows == 0) {
            throw new ServiceException("BUDGET_INSUFFICIENT 预算冻结金额不一致", 409);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long budgetId, BigDecimal amount, String bizType, Long bizId, int submissionNo, Long operatorUserId) {
        requireBudget(budgetId);
        String eventKey = "RELEASE:" + bizType + ":" + bizId + ":" + submissionNo;
        if (!insertLedger(budgetId, eventKey, OaBudgetLedger.ACTION_RELEASE, amount, bizType, bizId, operatorUserId)) {
            return;
        }
        int rows = budgetMapper.update(null, new LambdaUpdateWrapper<OaBudget>()
            .eq(OaBudget::getId, budgetId)
            .apply("frozen_amount >= {0}", amount)
            .setSql("frozen_amount = frozen_amount - " + literal(amount))
            .setSql("lock_version = lock_version + 1"));
        if (rows == 0) {
            throw new ServiceException("BUDGET_INSUFFICIENT 预算冻结金额不一致", 409);
        }
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 账本幂等闸门：同一 (budget_id,event_key) 只入账一次，重复事件返回 false 且不再扣减
     * （对齐请假额度账本口径，docs/04 14）。
     */
    private boolean insertLedger(Long budgetId, String eventKey, String action, BigDecimal amount,
                                 String bizType, Long bizId, Long operatorUserId) {
        long existing = ledgerMapper.selectCount(new LambdaQueryWrapper<OaBudgetLedger>()
            .eq(OaBudgetLedger::getBudgetId, budgetId)
            .eq(OaBudgetLedger::getEventKey, eventKey));
        if (existing > 0) {
            return false;
        }
        OaBudgetLedger ledger = new OaBudgetLedger();
        ledger.setBudgetId(budgetId);
        ledger.setEventKey(eventKey);
        ledger.setAction(action);
        ledger.setAmount(amount);
        ledger.setBizType(bizType);
        ledger.setBizId(bizId);
        ledger.setOperatorId(operatorUserId);
        ledger.setCreateTime(new Date());
        try {
            ledgerMapper.insert(ledger);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** 金额是服务端解析的 BigDecimal，格式化为普通数字字面量进条件 SQL */
    private String literal(BigDecimal amount) {
        return amount.toPlainString();
    }

    private OaBudget requireBudget(Long budgetId) {
        OaBudget budget = budgetId == null ? null : budgetMapper.selectById(budgetId);
        if (budget == null) {
            throw new ServiceException("预算不存在", 404);
        }
        return budget;
    }

    private String generateCode(int year) {
        return "BUD-" + year + "-" + System.nanoTime() % 100000;
    }

    private BudgetVo toVo(OaBudget budget) {
        if (budget == null) {
            return null;
        }
        BudgetVo vo = new BudgetVo();
        vo.setId(budget.getId());
        vo.setBudgetCode(budget.getBudgetCode());
        vo.setBudgetName(budget.getBudgetName());
        vo.setBudgetType(budget.getBudgetType());
        vo.setOwnerId(budget.getOwnerId());
        vo.setYear(budget.getYear());
        vo.setQuarter(budget.getQuarter());
        vo.setTotalAmount(budget.getTotalAmount());
        vo.setUsedAmount(budget.getUsedAmount());
        vo.setFrozenAmount(budget.getFrozenAmount());
        BigDecimal available = budget.getTotalAmount()
            .subtract(budget.getUsedAmount() == null ? BigDecimal.ZERO : budget.getUsedAmount())
            .subtract(budget.getFrozenAmount() == null ? BigDecimal.ZERO : budget.getFrozenAmount());
        vo.setAvailableAmount(available);
        vo.setWarnThreshold(budget.getWarnThreshold());
        BigDecimal consumed = (budget.getUsedAmount() == null ? BigDecimal.ZERO : budget.getUsedAmount())
            .add(budget.getFrozenAmount() == null ? BigDecimal.ZERO : budget.getFrozenAmount());
        boolean warn = budget.getTotalAmount() != null && budget.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
            && consumed.multiply(new BigDecimal("100"))
            .divide(budget.getTotalAmount(), 0, java.math.RoundingMode.HALF_UP)
            .intValue() >= (budget.getWarnThreshold() == null ? 80 : budget.getWarnThreshold());
        vo.setWarn(warn);
        vo.setStatus(budget.getStatus());
        vo.setRemark(budget.getRemark());
        return vo;
    }
}
