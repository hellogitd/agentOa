package org.dromara.agentoa.attendance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaLeaveBalance;
import org.dromara.agentoa.attendance.domain.OaLeaveLedger;
import org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
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
 * 额度分钟账本：冻结/结算/释放、事件幂等与防透支（docs/13 核心规则）。
 */
class LeaveBalanceH2Test {

    private static final int YEAR = 2026;

    @BeforeAll
    static void boot() {
        AttTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.logout();
        AttTestEnvironment.seedLeaveType(1, "annual", "年假", 1);
        AttTestEnvironment.seedLeaveType(2, "sick", "病假", 0);
    }

    private BalanceVo balanceOf(String leaveType) {
        return AttTestEnvironment.balanceService.selectBalances(AttTestEnvironment.USER_EMPLOYEE, YEAR).stream()
            .filter(row -> row.getLeaveType().equals(leaveType))
            .findFirst()
            .orElseThrow();
    }

    private void grant(String leaveType, int minutes) {
        BalanceGrantBo bo = new BalanceGrantBo();
        bo.setUserId(AttTestEnvironment.USER_EMPLOYEE);
        bo.setYear(YEAR);
        bo.setLeaveType(leaveType);
        bo.setMinutes(minutes);
        AttTestEnvironment.balanceService.grant(bo, "grant-" + leaveType + "-" + minutes, AttTestEnvironment.USER_HR);
    }

    @Test
    void grantAndFreezeTrackAvailability() {
        grant("annual", 4800);
        BalanceVo afterGrant = balanceOf("annual");
        assertThat(afterGrant.getTotalMinutes()).isEqualTo(4800);
        assertThat(afterGrant.getAvailableMinutes()).isEqualTo(4800);

        AttTestEnvironment.balanceService.freeze("leave", 11L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 480, AttTestEnvironment.USER_EMPLOYEE);
        BalanceVo afterFreeze = balanceOf("annual");
        assertThat(afterFreeze.getFrozenMinutes()).isEqualTo(480);
        assertThat(afterFreeze.getAvailableMinutes()).isEqualTo(4320);
    }

    @Test
    void freezeBeyondQuotaIsRejected() {
        grant("annual", 480);
        assertThatThrownBy(() -> AttTestEnvironment.inTransaction(() -> {
            AttTestEnvironment.balanceService.freeze("leave", 12L, 1,
                AttTestEnvironment.USER_EMPLOYEE, YEAR, "annual", 481, AttTestEnvironment.USER_EMPLOYEE);
            return null;
        }))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LEAVE_BALANCE_INSUFFICIENT");
        assertThat(balanceOf("annual").getFrozenMinutes()).isZero();
    }

    @Test
    void duplicateEventDoesNotDeductTwice() {
        grant("annual", 4800);
        AttTestEnvironment.balanceService.freeze("leave", 13L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 480, AttTestEnvironment.USER_EMPLOYEE);
        // 重复消费同一业务事件（例如回调重放）
        AttTestEnvironment.balanceService.freeze("leave", 13L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 480, AttTestEnvironment.USER_EMPLOYEE);
        assertThat(balanceOf("annual").getFrozenMinutes()).isEqualTo(480);

        AttTestEnvironment.balanceService.settle("leave", 13L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 480, AttTestEnvironment.USER_HR);
        AttTestEnvironment.balanceService.settle("leave", 13L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 480, AttTestEnvironment.USER_HR);
        BalanceVo settled = balanceOf("annual");
        assertThat(settled.getFrozenMinutes()).isZero();
        assertThat(settled.getUsedMinutes()).isEqualTo(480);

        List<OaLeaveLedger> events = AttTestEnvironment.ledgers.selectList(new LambdaQueryWrapper<OaLeaveLedger>()
            .orderByAsc(OaLeaveLedger::getId));
        assertThat(events).hasSize(3);
    }

    @Test
    void rejectAndRevokeReleaseFrozenMinutes() {
        grant("annual", 4800);
        AttTestEnvironment.balanceService.freeze("leave", 14L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 600, AttTestEnvironment.USER_EMPLOYEE);
        AttTestEnvironment.balanceService.release("leave", 14L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "annual", 600, AttTestEnvironment.USER_EMPLOYEE);
        BalanceVo released = balanceOf("annual");
        assertThat(released.getFrozenMinutes()).isZero();
        assertThat(released.getUsedMinutes()).isZero();
        assertThat(released.getAvailableMinutes()).isEqualTo(4800);
    }

    @Test
    void nonQuotaTypesAccumulateUsageWithoutTotalConstraint() {
        AttTestEnvironment.balanceService.freeze("leave", 15L, 1, AttTestEnvironment.USER_EMPLOYEE, YEAR,
            "sick", 480, AttTestEnvironment.USER_EMPLOYEE);
        BalanceVo sick = balanceOf("sick");
        assertThat(sick.getQuotaLimited()).isFalse();
        assertThat(sick.getAvailableMinutes()).isNull();
        assertThat(sick.getFrozenMinutes()).isEqualTo(480);
    }

    @Test
    void grantIsIdempotentByBusinessEvent() {
        BalanceGrantBo bo = new BalanceGrantBo();
        bo.setUserId(AttTestEnvironment.USER_EMPLOYEE);
        bo.setYear(YEAR);
        bo.setLeaveType("annual");
        bo.setMinutes(4800);
        AttTestEnvironment.balanceService.grant(bo, "same-event", AttTestEnvironment.USER_HR);
        AttTestEnvironment.balanceService.grant(bo, "same-event", AttTestEnvironment.USER_HR);
        assertThat(balanceOf("annual").getTotalMinutes()).isEqualTo(4800);
    }

    @Test
    void negativeAdjustmentCannotOverdrawUsedMinutes() {
        grant("annual", 480);
        assertThatThrownBy(() -> AttTestEnvironment.inTransaction(() -> {
            BalanceGrantBo bo = new BalanceGrantBo();
            bo.setUserId(AttTestEnvironment.USER_EMPLOYEE);
            bo.setYear(YEAR);
            bo.setLeaveType("annual");
            bo.setMinutes(-600);
            AttTestEnvironment.balanceService.grant(bo, "reclaim-600", AttTestEnvironment.USER_HR);
            return null;
        }))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LEAVE_BALANCE_INSUFFICIENT");
        OaLeaveBalance row = AttTestEnvironment.balances.selectOne(new LambdaQueryWrapper<OaLeaveBalance>()
            .eq(OaLeaveBalance::getUserId, AttTestEnvironment.USER_EMPLOYEE));
        assertThat(row.getTotalMinutes()).isEqualTo(480);
    }

    @Test
    void balancesCoverAllLeaveTypes() {
        List<BalanceVo> rows = AttTestEnvironment.balanceService
            .selectBalances(AttTestEnvironment.USER_EMPLOYEE, YEAR);
        assertThat(rows).extracting(BalanceVo::getLeaveType).containsExactly("annual", "sick");
        assertThat(rows.get(0).getExpireDate()).isNull();
        assertThat(LocalDate.of(YEAR, 1, 1)).isNotNull();
    }
}
