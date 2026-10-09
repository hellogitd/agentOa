package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaLeaveBalance;
import org.dromara.agentoa.attendance.domain.OaLeaveBatch;
import org.dromara.agentoa.attendance.domain.OaLeaveBatchAllocation;
import org.dromara.agentoa.attendance.domain.OaLeaveLedger;
import org.dromara.agentoa.attendance.domain.OaLeaveType;
import org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.enums.LedgerAction;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
import org.dromara.agentoa.attendance.domain.vo.BatchVo;
import org.dromara.agentoa.attendance.domain.vo.LedgerVo;
import org.dromara.agentoa.attendance.mapper.OaLeaveBalanceMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveBatchAllocationMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveBatchMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveLedgerMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveTypeMapper;
import org.dromara.agentoa.attendance.service.ILeaveBalanceService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 额度账本实现：账本事件先落库（唯一键做幂等闸门），余额条件更新后置；
 * 同一事务内失败全部回滚，避免半程扣减。
 * 调休/年假按批次滚动过期（AT-06）：批次 FIFO 先过期先扣，SUM(批次剩余)==余额可用；
 * 冻结过期保护至结算，释放回原批次（过期批次释放则归零）。
 */
@Service
@RequiredArgsConstructor
public class LeaveBalanceServiceImpl implements ILeaveBalanceService {

    /** 批次滚动过期适用假种（docs/09 第 16 节决策点 5：调休必做 + 年假） */
    private static final Set<String> BATCH_MANAGED_TYPES = Set.of("compensatory", "annual");

    private final OaLeaveTypeMapper leaveTypeMapper;
    private final OaLeaveBalanceMapper balanceMapper;
    private final OaLeaveLedgerMapper ledgerMapper;
    private final OaLeaveBatchMapper batchMapper;
    private final OaLeaveBatchAllocationMapper allocationMapper;

    @Override
    public List<BalanceVo> selectBalances(Long userId, Integer year) {
        int targetYear = year == null ? java.time.LocalDate.now().getYear() : year;
        List<OaLeaveType> types = leaveTypeMapper.selectList(new LambdaQueryWrapper<OaLeaveType>()
            .eq(OaLeaveType::getStatus, "0")
            .orderByAsc(OaLeaveType::getId));
        Map<String, OaLeaveBalance> balances = balanceMapper.selectList(new LambdaQueryWrapper<OaLeaveBalance>()
                .eq(OaLeaveBalance::getUserId, userId)
                .eq(OaLeaveBalance::getYear, targetYear))
            .stream()
            .collect(Collectors.toMap(OaLeaveBalance::getLeaveType, Function.identity(), (a, b) -> a));
        List<BalanceVo> rows = new ArrayList<>();
        for (OaLeaveType type : types) {
            rows.add(toBalanceVo(type, balances.get(type.getTypeCode()), targetYear));
        }
        return rows;
    }

    @Override
    public List<BatchVo> selectBatches(Long userId, Integer year, String leaveType) {
        LambdaQueryWrapper<OaLeaveBatch> query = new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getUserId, userId)
            .orderByAsc(OaLeaveBatch::getExpireDate)
            .orderByAsc(OaLeaveBatch::getId);
        if (year != null) {
            query.eq(OaLeaveBatch::getYear, year);
        }
        if (leaveType != null && !leaveType.isBlank()) {
            query.eq(OaLeaveBatch::getLeaveType, leaveType);
        }
        return batchMapper.selectList(query).stream().map(this::toBatchVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BalanceVo grant(BalanceGrantBo bo, String eventToken, Long operatorUserId) {
        OaLeaveType type = requireType(bo.getLeaveType());
        if (bo.getMinutes() == null || bo.getMinutes() == 0) {
            throw new ServiceException("额度分钟必须为非零整数", 400);
        }
        OaLeaveBalance balance = findOrCreateBalance(bo.getUserId(), bo.getEmployeeId(), bo.getYear(), type.getTypeCode());
        String eventKey = (eventToken == null || eventToken.isBlank())
            ? "GRANT:" + UUID.randomUUID()
            : "GRANT:" + eventToken.trim();
        LocalDate[] batchDates = isBatchManaged(type) && bo.getMinutes() > 0 ? batchDates(type, bo) : null;
        boolean applied = insertLedger(balance.getId(), eventKey, "grant", balance.getId(), 0,
            bo.getMinutes() > 0 ? LedgerAction.GRANT.name() : LedgerAction.ADJUST.name(),
            bo.getMinutes(), 0, 0, Math.abs(bo.getMinutes()), operatorUserId);
        if (!applied) {
            return toBalanceVo(type, balance, bo.getYear());
        }
        LambdaUpdateWrapper<OaLeaveBalance> update = new LambdaUpdateWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getId, balance.getId());
        if (bo.getMinutes() > 0) {
            update.setSql("total_minutes = total_minutes + " + bo.getMinutes());
        } else {
            update.apply("total_minutes + {0} >= frozen_minutes + used_minutes", bo.getMinutes())
                .setSql("total_minutes = total_minutes + " + bo.getMinutes());
        }
        update.setSql("lock_version = lock_version + 1");
        if (bo.getExpireDate() != null) {
            update.set(OaLeaveBalance::getExpireDate, bo.getExpireDate());
        }
        int rows = balanceMapper.update(null, update);
        if (rows == 0) {
            throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 额度回收后不足已用/冻结", 409);
        }
        if (isBatchManaged(type)) {
            if (bo.getMinutes() > 0) {
                createBatch(balance, type, bo, eventKey, batchDates);
            } else {
                reclaimFromBatches(balance, type, -bo.getMinutes());
            }
        }
        OaLeaveBalance latest = balanceMapper.selectById(balance.getId());
        return toBalanceVo(type, latest, bo.getYear());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                       String leaveType, int minutes, Long operatorUserId) {
        OaLeaveType type = requireType(leaveType);
        OaLeaveBalance balance = findOrCreateBalance(userId, null, year, type.getTypeCode());
        String eventKey = "FREEZE:" + businessType + ":" + businessId + ":" + submissionNo;
        boolean applied = insertLedger(balance.getId(), eventKey, businessType, businessId, submissionNo,
            LedgerAction.FREEZE.name(), 0, minutes, 0, minutes, operatorUserId);
        if (!applied) {
            return;
        }
        LambdaUpdateWrapper<OaLeaveBalance> update = new LambdaUpdateWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getId, balance.getId())
            .setSql("frozen_minutes = frozen_minutes + " + minutes + ", lock_version = lock_version + 1");
        if (isQuotaLimited(type)) {
            update.apply("total_minutes >= frozen_minutes + used_minutes + {0}", minutes);
        }
        int rows = balanceMapper.update(null, update);
        if (rows == 0) {
            throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 可用额度不足", 409);
        }
        if (isBatchManaged(type)) {
            allocateFreeze(balance, type, eventKey, minutes);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                       String leaveType, int minutes, Long operatorUserId) {
        OaLeaveType type = requireType(leaveType);
        OaLeaveBalance balance = findOrCreateBalance(userId, null, year, type.getTypeCode());
        String eventKey = "SETTLE:" + businessType + ":" + businessId + ":" + submissionNo;
        boolean applied = insertLedger(balance.getId(), eventKey, businessType, businessId, submissionNo,
            LedgerAction.SETTLE.name(), 0, -minutes, minutes, minutes, operatorUserId);
        if (!applied) {
            return;
        }
        int rows = balanceMapper.update(null, new LambdaUpdateWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getId, balance.getId())
            .apply("frozen_minutes >= {0}", minutes)
            .setSql("frozen_minutes = frozen_minutes - " + minutes
                + ", used_minutes = used_minutes + " + minutes
                + ", lock_version = lock_version + 1"));
        if (rows == 0) {
            throw new ServiceException("WF_STATE_CONFLICT 额度结算失败：冻结额度不足", 409);
        }
        if (isBatchManaged(type)) {
            applyToBatches(balance, type, "FREEZE:" + businessType + ":" + businessId + ":" + submissionNo,
                eventKey, minutes, OaLeaveBatchAllocation.ACTION_SETTLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                        String leaveType, int minutes, Long operatorUserId) {
        OaLeaveType type = requireType(leaveType);
        OaLeaveBalance balance = findOrCreateBalance(userId, null, year, type.getTypeCode());
        String eventKey = "RELEASE:" + businessType + ":" + businessId + ":" + submissionNo;
        boolean applied = insertLedger(balance.getId(), eventKey, businessType, businessId, submissionNo,
            LedgerAction.RELEASE.name(), 0, -minutes, 0, minutes, operatorUserId);
        if (!applied) {
            return;
        }
        int rows = balanceMapper.update(null, new LambdaUpdateWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getId, balance.getId())
            .apply("frozen_minutes >= {0}", minutes)
            .setSql("frozen_minutes = frozen_minutes - " + minutes + ", lock_version = lock_version + 1"));
        if (rows == 0) {
            throw new ServiceException("WF_STATE_CONFLICT 额度释放失败：冻结额度不足", 409);
        }
        if (isBatchManaged(type)) {
            int writeOff = applyToBatches(balance, type,
                "FREEZE:" + businessType + ":" + businessId + ":" + submissionNo,
                eventKey, minutes, OaLeaveBatchAllocation.ACTION_RELEASE);
            if (writeOff > 0) {
                // 过期批次释放归零：同步回收总额并留 ADJUST 流水（对账口径）
                String writeOffKey = "RELEASE-WO:" + businessType + ":" + businessId + ":" + submissionNo;
                boolean appliedWriteOff = insertLedger(balance.getId(), writeOffKey, businessType, businessId,
                    submissionNo, LedgerAction.ADJUST.name(), -writeOff, 0, 0, writeOff, operatorUserId);
                if (appliedWriteOff) {
                    balanceMapper.update(null, new LambdaUpdateWrapper<OaLeaveBalance>()
                        .eq(OaLeaveBalance::getId, balance.getId())
                        .apply("total_minutes >= {0}", writeOff)
                        .setSql("total_minutes = total_minutes - " + writeOff + ", lock_version = lock_version + 1"));
                }
            }
        }
    }

    @Override
    public PageVo<LedgerVo> selectLedger(Long userId, Integer year, String leaveType, AttendancePageQuery page) {
        LambdaQueryWrapper<OaLeaveLedger> query = new LambdaQueryWrapper<OaLeaveLedger>();
        if (userId != null || year != null || (leaveType != null && !leaveType.isBlank())) {
            LambdaQueryWrapper<OaLeaveBalance> balanceQuery = new LambdaQueryWrapper<OaLeaveBalance>();
            if (userId != null) {
                balanceQuery.eq(OaLeaveBalance::getUserId, userId);
            }
            if (year != null) {
                balanceQuery.eq(OaLeaveBalance::getYear, year);
            }
            if (leaveType != null && !leaveType.isBlank()) {
                balanceQuery.eq(OaLeaveBalance::getLeaveType, leaveType);
            }
            List<Long> balanceIds = balanceMapper.selectList(balanceQuery).stream()
                .map(OaLeaveBalance::getId).toList();
            if (balanceIds.isEmpty()) {
                return PageVo.of(List.of(), 0, page.safePageNum(), page.safePageSize());
            }
            query.in(OaLeaveLedger::getBalanceId, balanceIds);
        }
        query.orderByDesc(OaLeaveLedger::getCreateTime).orderByDesc(OaLeaveLedger::getId);
        IPage<OaLeaveLedger> result = ledgerMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<LedgerVo> records = MapstructUtils.convert(result.getRecords(), LedgerVo.class);
        return PageVo.of(records == null ? List.of() : records, result.getTotal(),
            page.safePageNum(), page.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireBatches(Long operatorUserId) {
        LocalDate today = LocalDate.now();
        List<OaLeaveBatch> due = batchMapper.selectList(new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_ACTIVE)
            .lt(OaLeaveBatch::getExpireDate, today)
            .orderByAsc(OaLeaveBatch::getExpireDate)
            .orderByAsc(OaLeaveBatch::getId));
        int expired = 0;
        for (OaLeaveBatch batch : due) {
            if (expireOne(batch, today, operatorUserId)) {
                expired++;
            }
        }
        return expired;
    }

    // ------------------------------------------------------------ batch bookkeeping (AT-06)

    private boolean isBatchManaged(OaLeaveType type) {
        return isQuotaLimited(type) && BATCH_MANAGED_TYPES.contains(type.getTypeCode());
    }

    /** 批次有效期：调休默认 +3 个月、年假默认当年 12-31；截止日必须晚于起始日 */
    private LocalDate[] batchDates(OaLeaveType type, BalanceGrantBo bo) {
        LocalDate validFrom = bo.getValidFrom() != null ? bo.getValidFrom() : LocalDate.now();
        LocalDate expireDate = bo.getExpireDate();
        if (expireDate == null) {
            expireDate = "compensatory".equals(type.getTypeCode())
                ? validFrom.plusMonths(3)
                : LocalDate.of(bo.getYear(), 12, 31);
        }
        if (!expireDate.isAfter(validFrom)) {
            throw new ServiceException("LEAVE_BATCH_INVALID 批次有效截止日必须晚于起始日", 400);
        }
        return new LocalDate[]{validFrom, expireDate};
    }

    /** 发放建批次：event_key 幂等 */
    private void createBatch(OaLeaveBalance balance, OaLeaveType type, BalanceGrantBo bo, String eventKey,
                             LocalDate[] dates) {
        LocalDate validFrom = dates[0];
        LocalDate expireDate = dates[1];
        OaLeaveBatch batch = new OaLeaveBatch();
        batch.setUserId(balance.getUserId());
        batch.setYear(balance.getYear());
        batch.setLeaveType(type.getTypeCode());
        batch.setBatchNo("B" + validFrom.toString().replace("-", "")
            + "-" + UUID.randomUUID().toString().substring(0, 6));
        batch.setGrantMinutes(bo.getMinutes());
        batch.setFrozenMinutes(0);
        batch.setUsedMinutes(0);
        batch.setExpiredMinutes(0);
        batch.setValidFrom(validFrom);
        batch.setExpireDate(expireDate);
        batch.setEventKey(eventKey);
        batch.setStatus(OaLeaveBatch.STATUS_ACTIVE);
        batch.setCreateTime(new Date());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            // 幂等重放：批次已创建
        }
    }

    /** 回收（负发放）从最晚过期批次 LIFO 缩减 grant，保持 SUM(批次剩余)==余额可用 */
    private void reclaimFromBatches(OaLeaveBalance balance, OaLeaveType type, int amount) {
        List<OaLeaveBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getUserId, balance.getUserId())
            .eq(OaLeaveBatch::getYear, balance.getYear())
            .eq(OaLeaveBatch::getLeaveType, type.getTypeCode())
            .orderByDesc(OaLeaveBatch::getExpireDate)
            .orderByDesc(OaLeaveBatch::getId));
        int need = amount;
        for (OaLeaveBatch batch : batches) {
            if (need <= 0) {
                break;
            }
            int take = Math.min(need, availableOf(batch));
            if (take <= 0) {
                continue;
            }
            int rows = batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
                .eq(OaLeaveBatch::getId, batch.getId())
                .apply("grant_minutes >= frozen_minutes + used_minutes + expired_minutes + {0}", take)
                .setSql("grant_minutes = grant_minutes - " + take));
            if (rows == 0) {
                continue;
            }
            refreshBatchStatus(batch.getId());
            need -= take;
        }
        if (need > 0) {
            throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 回收超出批次可用额度", 409);
        }
    }

    /** 冻结分配：先清到期批次（自愈），再按 expire_date ASC FIFO（先过期先扣），批次条件更新防透支 */
    private void allocateFreeze(OaLeaveBalance balance, OaLeaveType type, String eventKey, int minutes) {
        LocalDate today = LocalDate.now();
        for (OaLeaveBatch due : batchMapper.selectList(new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getUserId, balance.getUserId())
            .eq(OaLeaveBatch::getYear, balance.getYear())
            .eq(OaLeaveBatch::getLeaveType, type.getTypeCode())
            .eq(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_ACTIVE)
            .lt(OaLeaveBatch::getExpireDate, today))) {
            expireOne(due, today, 0L);
        }
        List<OaLeaveBatch> batches = consumableBatches(balance, type, today);
        int need = minutes;
        for (OaLeaveBatch batch : batches) {
            if (need <= 0) {
                break;
            }
            int take = Math.min(need, availableOf(batch));
            if (take <= 0) {
                continue;
            }
            int rows = batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
                .eq(OaLeaveBatch::getId, batch.getId())
                .eq(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_ACTIVE)
                .ge(OaLeaveBatch::getExpireDate, today)
                .apply("grant_minutes >= frozen_minutes + used_minutes + expired_minutes + {0}", take)
                .setSql("frozen_minutes = frozen_minutes + " + take));
            if (rows == 0) {
                // 并发抢同一批次失败：跳到下一批
                continue;
            }
            insertAllocation(batch.getId(), eventKey, take, OaLeaveBatchAllocation.ACTION_FREEZE);
            refreshBatchStatus(batch.getId());
            need -= take;
        }
        if (need > 0) {
            throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 批次可用额度不足", 409);
        }
    }

    /**
     * 结算/释放作用到批次：优先按冻结分配原批回退（释放回原批次），
     * 无分配记录（历史在途冻结）时按冻结 FIFO 兜底；过期批次释放归零、结算仍成功。
     * 返回释放归零分钟（供调用方回收总额）。
     */
    private int applyToBatches(OaLeaveBalance balance, OaLeaveType type, String freezeEventKey,
                               String eventKey, int minutes, String action) {
        List<OaLeaveBatchAllocation> freezes = allocationMapper.selectList(
            new LambdaQueryWrapper<OaLeaveBatchAllocation>()
                .eq(OaLeaveBatchAllocation::getLedgerEventKey, freezeEventKey)
                .eq(OaLeaveBatchAllocation::getAction, OaLeaveBatchAllocation.ACTION_FREEZE));
        List<long[]> plan = new ArrayList<>();
        if (!freezes.isEmpty()) {
            int need = minutes;
            for (OaLeaveBatchAllocation freeze : freezes) {
                if (need <= 0) {
                    break;
                }
                int take = Math.min(need, freeze.getMinutes());
                plan.add(new long[]{freeze.getBatchId(), take});
                need -= take;
            }
        } else {
            int need = minutes;
            for (OaLeaveBatch batch : batchMapper.selectList(new LambdaQueryWrapper<OaLeaveBatch>()
                .eq(OaLeaveBatch::getUserId, balance.getUserId())
                .eq(OaLeaveBatch::getYear, balance.getYear())
                .eq(OaLeaveBatch::getLeaveType, type.getTypeCode())
                .orderByAsc(OaLeaveBatch::getExpireDate)
                .orderByAsc(OaLeaveBatch::getId))) {
                if (need <= 0) {
                    break;
                }
                int take = Math.min(need, batch.getFrozenMinutes() == null ? 0 : batch.getFrozenMinutes());
                if (take <= 0) {
                    continue;
                }
                plan.add(new long[]{batch.getId(), take});
                need -= take;
            }
        }
        int writeOffTotal = 0;
        for (long[] slice : plan) {
            Long batchId = slice[0];
            int take = (int) slice[1];
            OaLeaveBatch batch = batchMapper.selectById(batchId);
            if (batch == null) {
                throw new ServiceException("LEAVE_BATCH_NOT_FOUND 批次不存在: " + batchId, 409);
            }
            boolean writeOff = OaLeaveBatchAllocation.ACTION_RELEASE.equals(action) && isExpired(batch);
            int rows = writeOff
                ? batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
                    .eq(OaLeaveBatch::getId, batchId)
                    .apply("frozen_minutes >= {0}", take)
                    .setSql("frozen_minutes = frozen_minutes - " + take
                        + ", expired_minutes = expired_minutes + " + take))
                : batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
                    .eq(OaLeaveBatch::getId, batchId)
                    .apply("frozen_minutes >= {0}", take)
                    .setSql("frozen_minutes = frozen_minutes - " + take
                        + (OaLeaveBatchAllocation.ACTION_SETTLE.equals(action)
                            ? ", used_minutes = used_minutes + " + take : "")));
            if (rows == 0) {
                throw new ServiceException("WF_STATE_CONFLICT 批次冻结额度不足: " + batch.getBatchNo(), 409);
            }
            insertAllocation(batchId, eventKey, take, action);
            refreshBatchStatus(batchId);
            if (writeOff) {
                writeOffTotal += take;
            }
        }
        return writeOffTotal;
    }

    /** 批次过期：清零可用（冻结保护至结算），EXPIRE:{batchId} 幂等 */
    private boolean expireOne(OaLeaveBatch batch, LocalDate today, Long operatorUserId) {
        int available = availableOf(batch);
        OaLeaveBalance balance = findOrCreateBalance(batch.getUserId(), null, batch.getYear(), batch.getLeaveType());
        String eventKey = "EXPIRE:" + batch.getId();
        if (available > 0) {
            boolean applied = insertLedger(balance.getId(), eventKey, "expire", batch.getId(), 0,
                LedgerAction.ADJUST.name(), -available, 0, 0, available, operatorUserId);
            if (!applied) {
                markExpired(batch.getId());
                return false;
            }
            int rows = balanceMapper.update(null, new LambdaUpdateWrapper<OaLeaveBalance>()
                .eq(OaLeaveBalance::getId, balance.getId())
                .apply("total_minutes >= frozen_minutes + used_minutes + {0}", available)
                .setSql("total_minutes = total_minutes - " + available + ", lock_version = lock_version + 1"));
            if (rows == 0) {
                throw new ServiceException("LEAVE_BALANCE_INSUFFICIENT 过期清零后不足已用/冻结", 409);
            }
            batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
                .eq(OaLeaveBatch::getId, batch.getId())
                .setSql("expired_minutes = expired_minutes + " + available)
                .set(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_EXPIRED));
            insertAllocation(batch.getId(), eventKey, available, OaLeaveBatchAllocation.ACTION_EXPIRE);
            return true;
        }
        markExpired(batch.getId());
        return false;
    }

    private void markExpired(Long batchId) {
        batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getId, batchId)
            .eq(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_ACTIVE)
            .set(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_EXPIRED));
    }

    private void refreshBatchStatus(Long batchId) {
        batchMapper.update(null, new LambdaUpdateWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getId, batchId)
            .ne(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_EXPIRED)
            .setSql("status = CASE WHEN grant_minutes - frozen_minutes - used_minutes - expired_minutes <= 0 "
                + "THEN " + OaLeaveBatch.STATUS_EXHAUSTED + " ELSE " + OaLeaveBatch.STATUS_ACTIVE + " END"));
    }

    private List<OaLeaveBatch> consumableBatches(OaLeaveBalance balance, OaLeaveType type, LocalDate today) {
        return batchMapper.selectList(new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getUserId, balance.getUserId())
            .eq(OaLeaveBatch::getYear, balance.getYear())
            .eq(OaLeaveBatch::getLeaveType, type.getTypeCode())
            .eq(OaLeaveBatch::getStatus, OaLeaveBatch.STATUS_ACTIVE)
            .ge(OaLeaveBatch::getExpireDate, today)
            .orderByAsc(OaLeaveBatch::getExpireDate)
            .orderByAsc(OaLeaveBatch::getId));
    }

    private int availableOf(OaLeaveBatch batch) {
        int grant = batch.getGrantMinutes() == null ? 0 : batch.getGrantMinutes();
        int frozen = batch.getFrozenMinutes() == null ? 0 : batch.getFrozenMinutes();
        int used = batch.getUsedMinutes() == null ? 0 : batch.getUsedMinutes();
        int expired = batch.getExpiredMinutes() == null ? 0 : batch.getExpiredMinutes();
        return grant - frozen - used - expired;
    }

    private boolean isExpired(OaLeaveBatch batch) {
        return batch.getStatus() != null && batch.getStatus() == OaLeaveBatch.STATUS_EXPIRED
            || (batch.getExpireDate() != null && batch.getExpireDate().isBefore(LocalDate.now()));
    }

    private void insertAllocation(Long batchId, String ledgerEventKey, int minutes, String action) {
        OaLeaveBatchAllocation allocation = new OaLeaveBatchAllocation();
        allocation.setBatchId(batchId);
        allocation.setLedgerEventKey(ledgerEventKey);
        allocation.setMinutes(minutes);
        allocation.setAction(action);
        allocation.setCreateTime(new Date());
        allocationMapper.insert(allocation);
    }

    private BatchVo toBatchVo(OaLeaveBatch batch) {
        BatchVo vo = new BatchVo();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setYear(batch.getYear());
        vo.setLeaveType(batch.getLeaveType());
        vo.setGrantMinutes(batch.getGrantMinutes());
        vo.setFrozenMinutes(batch.getFrozenMinutes());
        vo.setUsedMinutes(batch.getUsedMinutes());
        vo.setExpiredMinutes(batch.getExpiredMinutes());
        vo.setAvailableMinutes(availableOf(batch));
        vo.setValidFrom(batch.getValidFrom());
        vo.setExpireDate(batch.getExpireDate());
        vo.setStatus(batch.getStatus());
        return vo;
    }

    // ------------------------------------------------------------ shared ledger helpers

    private OaLeaveType requireType(String leaveType) {
        OaLeaveType type = leaveTypeMapper.selectOne(new LambdaQueryWrapper<OaLeaveType>()
            .eq(OaLeaveType::getTypeCode, leaveType)
            .last("LIMIT 1"));
        if (type == null || "1".equals(type.getStatus())) {
            throw new ServiceException("假种不存在或已停用: " + leaveType, 400);
        }
        return type;
    }

    private boolean isQuotaLimited(OaLeaveType type) {
        return type.getQuotaLimited() == null || type.getQuotaLimited() == 1;
    }

    private OaLeaveBalance findOrCreateBalance(Long userId, Long employeeId, Integer year, String leaveType) {
        OaLeaveBalance existing = balanceMapper.selectOne(new LambdaQueryWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getUserId, userId)
            .eq(OaLeaveBalance::getYear, year)
            .eq(OaLeaveBalance::getLeaveType, leaveType)
            .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        OaLeaveBalance balance = new OaLeaveBalance();
        balance.setUserId(userId);
        balance.setEmployeeId(employeeId);
        balance.setYear(year);
        balance.setLeaveType(leaveType);
        balance.setTotalMinutes(0);
        balance.setFrozenMinutes(0);
        balance.setUsedMinutes(0);
        balance.setLockVersion(0);
        balance.setCreateTime(new Date());
        try {
            balanceMapper.insert(balance);
        } catch (DuplicateKeyException e) {
            return balanceMapper.selectOne(new LambdaQueryWrapper<OaLeaveBalance>()
                .eq(OaLeaveBalance::getUserId, userId)
                .eq(OaLeaveBalance::getYear, year)
                .eq(OaLeaveBalance::getLeaveType, leaveType)
                .last("LIMIT 1"));
        }
        return balance;
    }

    /** 账本事件先落库：唯一键 (balance_id, event_key) 是幂等闸门 */
    private boolean insertLedger(Long balanceId, String eventKey, String businessType, Long businessId,
                                 int submissionNo, String action, int totalDelta, int frozenDelta,
                                 int usedDelta, int leaveMinutes, Long operatorId) {
        long existing = ledgerMapper.selectCount(new LambdaQueryWrapper<OaLeaveLedger>()
            .eq(OaLeaveLedger::getBalanceId, balanceId)
            .eq(OaLeaveLedger::getEventKey, eventKey));
        if (existing > 0) {
            return false;
        }
        OaLeaveLedger ledger = new OaLeaveLedger();
        ledger.setBalanceId(balanceId);
        ledger.setEventKey(eventKey);
        ledger.setBusinessType(businessType);
        ledger.setBusinessId(businessId);
        ledger.setSubmissionNo(submissionNo);
        ledger.setAction(action);
        ledger.setTotalDelta(totalDelta);
        ledger.setFrozenDelta(frozenDelta);
        ledger.setUsedDelta(usedDelta);
        ledger.setLeaveMinutes(leaveMinutes);
        ledger.setOperatorId(operatorId == null ? 0L : operatorId);
        ledger.setCreateTime(new Date());
        try {
            ledgerMapper.insert(ledger);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private BalanceVo toBalanceVo(OaLeaveType type, OaLeaveBalance balance, int year) {
        BalanceVo vo = new BalanceVo();
        vo.setLeaveType(type.getTypeCode());
        vo.setTypeName(type.getTypeName());
        vo.setYear(year);
        vo.setQuotaLimited(isQuotaLimited(type));
        int total = balance == null || balance.getTotalMinutes() == null ? 0 : balance.getTotalMinutes();
        int frozen = balance == null || balance.getFrozenMinutes() == null ? 0 : balance.getFrozenMinutes();
        int used = balance == null || balance.getUsedMinutes() == null ? 0 : balance.getUsedMinutes();
        vo.setId(balance == null ? null : balance.getId());
        vo.setTotalMinutes(total);
        vo.setFrozenMinutes(frozen);
        vo.setUsedMinutes(used);
        vo.setAvailableMinutes(isQuotaLimited(type) ? total - frozen - used : null);
        vo.setExpireDate(balance == null ? null : balance.getExpireDate());
        return vo;
    }
}
