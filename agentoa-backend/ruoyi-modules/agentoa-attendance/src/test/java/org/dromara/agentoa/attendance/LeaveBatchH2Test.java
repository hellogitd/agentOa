package org.dromara.agentoa.attendance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaLeaveBalance;
import org.dromara.agentoa.attendance.domain.OaLeaveBatch;
import org.dromara.agentoa.attendance.domain.OaLeaveBatchAllocation;
import org.dromara.agentoa.attendance.domain.OaLeaveLedger;
import org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
import org.dromara.agentoa.attendance.domain.vo.BatchVo;
import org.dromara.agentoa.attendance.support.AttTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 调休/年假批次滚动过期（AT-06）：FIFO 先过期先扣、allocation 对账、过期清零幂等、
 * 冻结过期保护至结算、释放回原批次（过期释放归零）。
 */
class LeaveBatchH2Test {

    private static final int YEAR = 2026;
    private static final LocalDate TODAY = LocalDate.now();

    @BeforeAll
    static void boot() {
        AttTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.logout();
        AttTestEnvironment.seedLeaveType(1, "annual", "年假", 1);
        AttTestEnvironment.seedLeaveType(2, "compensatory", "调休", 1);
    }

    private void grant(String leaveType, int minutes, String token, LocalDate validFrom, LocalDate expireDate) {
        BalanceGrantBo bo = new BalanceGrantBo();
        bo.setUserId(AttTestEnvironment.USER_EMPLOYEE);
        bo.setYear(YEAR);
        bo.setLeaveType(leaveType);
        bo.setMinutes(minutes);
        bo.setValidFrom(validFrom);
        bo.setExpireDate(expireDate);
        AttTestEnvironment.balanceService.grant(bo, token, AttTestEnvironment.USER_HR);
    }

    private BalanceVo balanceOf(String leaveType) {
        return AttTestEnvironment.balanceService.selectBalances(AttTestEnvironment.USER_EMPLOYEE, YEAR).stream()
            .filter(row -> row.getLeaveType().equals(leaveType))
            .findFirst()
            .orElseThrow();
    }

    private List<BatchVo> batchesOf(String leaveType) {
        return AttTestEnvironment.balanceService.selectBatches(AttTestEnvironment.USER_EMPLOYEE, YEAR, leaveType);
    }

    private BatchVo batchByToken(String token) {
        OaLeaveBatch row = AttTestEnvironment.batches.selectOne(new LambdaQueryWrapper<OaLeaveBatch>()
            .eq(OaLeaveBatch::getEventKey, "GRANT:" + token));
        return batchesOf(row.getLeaveType()).stream().filter(b -> b.getId().equals(row.getId())).findFirst().orElseThrow();
    }

    private long allocationCount(String action) {
        return AttTestEnvironment.batchAllocations.selectCount(new LambdaQueryWrapper<OaLeaveBatchAllocation>()
            .eq(OaLeaveBatchAllocation::getAction, action));
    }

    @Test
    void grantDefaultsAndValidation() {
        grant("compensatory", 300, "g1", null, null);
        BatchVo comp = batchByToken("g1");
        assertThat(comp.getValidFrom()).isEqualTo(TODAY);
        assertThat(comp.getExpireDate()).isEqualTo(TODAY.plusMonths(3));
        assertThat(comp.getAvailableMinutes()).isEqualTo(300);
        assertThat(comp.getStatus()).isEqualTo(1);

        grant("annual", 400, "g2", null, null);
        BatchVo annual = batchByToken("g2");
        assertThat(annual.getExpireDate()).isEqualTo(LocalDate.of(YEAR, 12, 31));

        assertThatThrownBy(() -> grant("compensatory", 100, "g3", TODAY, TODAY.minusDays(1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LEAVE_BATCH_INVALID");
    }

    @Test
    void fifoConsumesEarliestExpiryFirst() {
        grant("compensatory", 500, "early", TODAY.minusDays(5), TODAY.plusDays(5));
        grant("compensatory", 500, "late", TODAY, TODAY.plusDays(30));

        AttTestEnvironment.balanceService.freeze("leave", 101L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 700, AttTestEnvironment.USER_EMPLOYEE);

        BatchVo early = batchByToken("early");
        BatchVo late = batchByToken("late");
        assertThat(early.getFrozenMinutes()).isEqualTo(500);
        assertThat(early.getAvailableMinutes()).isZero();
        assertThat(late.getFrozenMinutes()).isEqualTo(200);
        assertThat(late.getAvailableMinutes()).isEqualTo(300);
        assertThat(allocationCount("FREEZE")).isEqualTo(2);
    }

    @Test
    void freezeAcrossBatchesCannotOverdraft() {
        grant("compensatory", 500, "b1", TODAY, TODAY.plusDays(10));
        grant("compensatory", 500, "b2", TODAY, TODAY.plusDays(20));

        assertThatThrownBy(() -> AttTestEnvironment.inTransaction(() -> {
            AttTestEnvironment.balanceService.freeze("leave", 102L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
                "compensatory", 1100, AttTestEnvironment.USER_EMPLOYEE);
            return null;
        }))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LEAVE_BALANCE_INSUFFICIENT");
        assertThat(balanceOf("compensatory").getFrozenMinutes()).isZero();
        assertThat(batchByToken("b1").getFrozenMinutes()).isZero();
        assertThat(batchByToken("b2").getFrozenMinutes()).isZero();
    }

    @Test
    void settleAndReleaseReconcileAllocations() {
        grant("compensatory", 500, "s1", TODAY, TODAY.plusDays(5));
        grant("compensatory", 500, "s2", TODAY, TODAY.plusDays(30));
        AttTestEnvironment.balanceService.freeze("leave", 103L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 700, AttTestEnvironment.USER_EMPLOYEE);

        AttTestEnvironment.balanceService.settle("leave", 103L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 700, AttTestEnvironment.USER_HR);

        assertThat(batchByToken("s1").getUsedMinutes()).isEqualTo(500);
        assertThat(batchByToken("s2").getUsedMinutes()).isEqualTo(200);
        assertThat(batchByToken("s1").getFrozenMinutes()).isZero();
        assertThat(allocationCount("FREEZE")).isEqualTo(2);
        assertThat(allocationCount("SETTLE")).isEqualTo(2);
        BalanceVo settled = balanceOf("compensatory");
        assertThat(settled.getUsedMinutes()).isEqualTo(700);
        assertThat(settled.getFrozenMinutes()).isZero();
    }

    @Test
    void releaseReturnsToOriginalBatches() {
        grant("compensatory", 500, "r1", TODAY, TODAY.plusDays(5));
        grant("compensatory", 500, "r2", TODAY, TODAY.plusDays(30));
        AttTestEnvironment.balanceService.freeze("leave", 104L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 700, AttTestEnvironment.USER_EMPLOYEE);

        AttTestEnvironment.balanceService.release("leave", 104L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 700, AttTestEnvironment.USER_EMPLOYEE);

        BatchVo r1 = batchByToken("r1");
        BatchVo r2 = batchByToken("r2");
        assertThat(r1.getFrozenMinutes()).isZero();
        assertThat(r1.getAvailableMinutes()).isEqualTo(500);
        assertThat(r2.getAvailableMinutes()).isEqualTo(500);
        assertThat(allocationCount("RELEASE")).isEqualTo(2);
        assertThat(balanceOf("compensatory").getAvailableMinutes()).isEqualTo(1000);
    }

    @Test
    void expiryZeroesAvailableAndIsIdempotent() {
        grant("compensatory", 1000, "x1", TODAY.minusDays(20), TODAY.minusDays(1));

        assertThat(AttTestEnvironment.balanceService.expireBatches(AttTestEnvironment.USER_HR)).isEqualTo(1);

        BalanceVo after = balanceOf("compensatory");
        assertThat(after.getTotalMinutes()).isZero();
        BatchVo expired = batchByToken("x1");
        assertThat(expired.getStatus()).isEqualTo(2);
        assertThat(expired.getExpiredMinutes()).isEqualTo(1000);
        assertThat(allocationCount("EXPIRE")).isEqualTo(1);

        assertThat(AttTestEnvironment.balanceService.expireBatches(AttTestEnvironment.USER_HR)).isZero();
        assertThat(balanceOf("compensatory").getTotalMinutes()).isZero();
        List<OaLeaveLedger> expireEvents = AttTestEnvironment.ledgers.selectList(
            new LambdaQueryWrapper<OaLeaveLedger>().eq(OaLeaveLedger::getEventKey, "EXPIRE:" + expired.getId()));
        assertThat(expireEvents).hasSize(1);
    }

    @Test
    void expiryProtectsFrozenUntilSettle() {
        grant("compensatory", 1000, "p1", TODAY, TODAY.plusDays(30));
        AttTestEnvironment.balanceService.freeze("leave", 105L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 300, AttTestEnvironment.USER_EMPLOYEE);
        forceExpiry("GRANT:p1", TODAY.minusDays(1));

        assertThat(AttTestEnvironment.balanceService.expireBatches(AttTestEnvironment.USER_HR)).isEqualTo(1);

        BatchVo expired = batchByToken("p1");
        assertThat(expired.getExpiredMinutes()).isEqualTo(700);
        assertThat(expired.getFrozenMinutes()).isEqualTo(300);
        BalanceVo balance = balanceOf("compensatory");
        assertThat(balance.getTotalMinutes()).isEqualTo(300);
        assertThat(balance.getFrozenMinutes()).isEqualTo(300);
        assertThat(balance.getAvailableMinutes()).isZero();

        AttTestEnvironment.balanceService.settle("leave", 105L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 300, AttTestEnvironment.USER_HR);
        BatchVo settled = batchByToken("p1");
        assertThat(settled.getFrozenMinutes()).isZero();
        assertThat(settled.getUsedMinutes()).isEqualTo(300);
        assertThat(balanceOf("compensatory").getUsedMinutes()).isEqualTo(300);
    }

    @Test
    void releaseAfterExpiryWritesOff() {
        grant("compensatory", 1000, "w1", TODAY, TODAY.plusDays(30));
        AttTestEnvironment.balanceService.freeze("leave", 106L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 300, AttTestEnvironment.USER_EMPLOYEE);
        forceExpiry("GRANT:w1", TODAY.minusDays(1));
        assertThat(AttTestEnvironment.balanceService.expireBatches(AttTestEnvironment.USER_HR)).isEqualTo(1);

        AttTestEnvironment.balanceService.release("leave", 106L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 300, AttTestEnvironment.USER_EMPLOYEE);

        BatchVo writtenOff = batchByToken("w1");
        assertThat(writtenOff.getFrozenMinutes()).isZero();
        assertThat(writtenOff.getExpiredMinutes()).isEqualTo(1000);
        BalanceVo balance = balanceOf("compensatory");
        assertThat(balance.getTotalMinutes()).isZero();
        assertThat(balance.getAvailableMinutes()).isZero();
        // 归零留 ADJUST 流水，SUM(批次剩余)==余额可用 对账成立
        List<OaLeaveLedger> adjust = AttTestEnvironment.ledgers.selectList(new LambdaQueryWrapper<OaLeaveLedger>()
            .eq(OaLeaveLedger::getAction, "ADJUST"));
        assertThat(adjust).hasSize(2);
    }

    @Test
    void batchSumsReconcileWithBalance() {
        grant("compensatory", 400, "m1", TODAY, TODAY.plusDays(5));
        grant("compensatory", 600, "m2", TODAY, TODAY.plusDays(30));
        AttTestEnvironment.balanceService.freeze("leave", 107L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 500, AttTestEnvironment.USER_EMPLOYEE);
        AttTestEnvironment.balanceService.settle("leave", 107L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 200, AttTestEnvironment.USER_HR);
        AttTestEnvironment.balanceService.release("leave", 107L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "compensatory", 100, AttTestEnvironment.USER_EMPLOYEE);

        List<BatchVo> batches = batchesOf("compensatory");
        int sumAvailable = batches.stream().mapToInt(BatchVo::getAvailableMinutes).sum();
        int sumFrozen = batches.stream().mapToInt(BatchVo::getFrozenMinutes).sum();
        int sumUsed = batches.stream().mapToInt(BatchVo::getUsedMinutes).sum();
        BalanceVo balance = balanceOf("compensatory");
        assertThat(balance.getTotalMinutes()).isEqualTo(1000);
        assertThat(balance.getAvailableMinutes()).isEqualTo(sumAvailable);
        assertThat(balance.getFrozenMinutes()).isEqualTo(sumFrozen);
        assertThat(balance.getUsedMinutes()).isEqualTo(sumUsed);
    }

    @Test
    void reclaimReducesLatestExpiryFirst() {
        grant("compensatory", 300, "c1", TODAY, TODAY.plusDays(5));
        grant("compensatory", 300, "c2", TODAY, TODAY.plusDays(30));
        grant("compensatory", -200, "reclaim", TODAY, null);

        assertThat(batchByToken("c2").getGrantMinutes()).isEqualTo(100);
        assertThat(batchByToken("c1").getGrantMinutes()).isEqualTo(300);
        assertThat(balanceOf("compensatory").getTotalMinutes()).isEqualTo(400);
    }

    private void forceExpiry(String batchEventKey, LocalDate expireDate) {
        AttTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_leave_batch SET expire_date = ? WHERE event_key = ?", expireDate, batchEventKey);
    }
}
