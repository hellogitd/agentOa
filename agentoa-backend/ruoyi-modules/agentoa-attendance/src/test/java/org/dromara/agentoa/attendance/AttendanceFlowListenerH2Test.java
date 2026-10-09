package org.dromara.agentoa.attendance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.OaCorrection;
import org.dromara.agentoa.attendance.domain.OaOvertime;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.support.AttTestEnvironment;
import org.dromara.agentoa.workflow.domain.OaLeaveRequest;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 流程回调承接：请假额度冻结/结算/释放、加班与补卡生效（docs/13 第 6 步）。
 */
class AttendanceFlowListenerH2Test {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final Long USER = AttTestEnvironment.USER_EMPLOYEE;

    @BeforeAll
    static void boot() {
        AttTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.logout();
        AttTestEnvironment.seedUser(USER, AttTestEnvironment.DEPT_A, "张三");
        AttTestEnvironment.seedLeaveType(1, "annual", "年假", 1);
        AttTestEnvironment.seedLeaveType(2, "sick", "病假", 0);
        AttTestEnvironment.seedShift(1L, "FIXED", LocalTime.of(9, 0), LocalTime.of(18, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), 0, 0, 0, 120, 240);
        AttTestEnvironment.seedGroup(1L, "G1", 1L, "1,2,3,4,5");
        AttTestEnvironment.seedMember(1L, 1L, USER, LocalDate.of(2026, 1, 1), null);
    }

    private void grantAnnual(int minutes) {
        org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo bo = new org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo();
        bo.setUserId(USER);
        bo.setYear(DAY.getYear());
        bo.setLeaveType("annual");
        bo.setMinutes(minutes);
        AttTestEnvironment.balanceService.grant(bo, "grant-" + minutes, AttTestEnvironment.USER_HR);
    }

    private OaAttendanceDay dayRow(LocalDate date) {
        return AttTestEnvironment.days.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, USER)
            .eq(OaAttendanceDay::getAttendanceDate, date));
    }

    @Test
    void leaveLifecycleFreezesSettlesAndRecomputesDay() {
        grantAnnual(4800);
        OaLeaveRequest request = AttTestEnvironment.seedLeaveRequest(11L, USER, "annual",
            LocalDateTime.of(DAY, LocalTime.of(9, 0)), LocalDateTime.of(DAY, LocalTime.of(18, 0)), 480, 1, 0);

        AttTestEnvironment.flowListener.onBusinessValidated(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, request.getId(), USER);
        AttTestEnvironment.flowListener.onBusinessStarted(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, request.getId(), 900L, 1);

        var frozen = AttTestEnvironment.balanceService.selectBalances(USER, DAY.getYear()).get(0);
        assertThat(frozen.getFrozenMinutes()).isEqualTo(480);

        request.setStatus(3);
        request.setSubmissionNo(1);
        AttTestEnvironment.leaveRequests.updateById(request);
        AttTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, request.getId(), 900L, AttTestEnvironment.USER_HR);

        var settled = AttTestEnvironment.balanceService.selectBalances(USER, DAY.getYear()).get(0);
        assertThat(settled.getFrozenMinutes()).isZero();
        assertThat(settled.getUsedMinutes()).isEqualTo(480);

        OaAttendanceDay day = dayRow(DAY);
        assertThat(day.getWorkStatus()).isEqualTo(4);
        assertThat(day.getLeaveMinutes()).isEqualTo(480);
    }

    @Test
    void revokeReleasesFrozenMinutes() {
        grantAnnual(4800);
        OaLeaveRequest request = AttTestEnvironment.seedLeaveRequest(12L, USER, "annual",
            LocalDateTime.of(DAY, LocalTime.of(9, 0)), LocalDateTime.of(DAY, LocalTime.of(12, 0)), 180, 2, 1);
        AttTestEnvironment.flowListener.onBusinessStarted(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, request.getId(), 901L, 1);
        AttTestEnvironment.flowListener.onBusinessRevoked(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, request.getId(), 901L, USER);
        var balance = AttTestEnvironment.balanceService.selectBalances(USER, DAY.getYear()).get(0);
        assertThat(balance.getFrozenMinutes()).isZero();
        assertThat(balance.getAvailableMinutes()).isEqualTo(4800);
    }

    @Test
    void insufficientQuotaBlocksSubmission() {
        grantAnnual(60);
        AttTestEnvironment.seedLeaveRequest(13L, USER, "annual",
            LocalDateTime.of(DAY, LocalTime.of(9, 0)), LocalDateTime.of(DAY, LocalTime.of(18, 0)), 480, 1, 0);
        assertThatThrownBy(() -> AttTestEnvironment.flowListener.onBusinessValidated(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, 13L, USER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LEAVE_BALANCE_INSUFFICIENT");
    }

    @Test
    void overlappingLeaveIsRejected() {
        grantAnnual(4800);
        AttTestEnvironment.seedLeaveRequest(14L, USER, "annual",
            LocalDateTime.of(DAY, LocalTime.of(9, 0)), LocalDateTime.of(DAY, LocalTime.of(12, 0)), 180, 3, 1);
        AttTestEnvironment.seedLeaveRequest(15L, USER, "annual",
            LocalDateTime.of(DAY, LocalTime.of(11, 0)), LocalDateTime.of(DAY, LocalTime.of(14, 0)), 120, 1, 0);
        assertThatThrownBy(() -> AttTestEnvironment.flowListener.onBusinessValidated(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.LEAVE, 15L, USER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("WF_STATE_CONFLICT");
    }

    @Test
    void approvedCorrectionCreatesPunchOnceAndRecomputesDay() {
        LocalDateTime corrected = LocalDateTime.of(DAY, LocalTime.of(9, 0));
        AttTestEnvironment.seedCorrectionRequest(21L, USER, DAY, 1, corrected, 2);
        AttTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.CORRECTION, 21L, 902L, AttTestEnvironment.USER_HR);
        AttTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.CORRECTION, 21L, 902L, AttTestEnvironment.USER_HR);

        assertThat(AttTestEnvironment.punches.selectCount(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getCorrectionRequestId, 21L))).isEqualTo(1);
        assertThat(AttTestEnvironment.corrections.selectCount(new LambdaQueryWrapper<OaCorrection>()
            .eq(OaCorrection::getCorrectionRequestId, 21L))).isEqualTo(1);
        OaAttendanceDay day = dayRow(DAY);
        assertThat(day.getWorkStatus()).isEqualTo(1);
        assertThat(day.getFirstPunchTime()).isEqualTo(corrected);
    }

    @Test
    void correctionMonthlyLimitIsEnforced() {
        for (int i = 0; i < AttendanceFlowListenerH2Test.MAX_LIMIT; i++) {
            AttTestEnvironment.seedCorrectionRequest(30L + i, USER, DAY.plusDays(i), 1,
                LocalDateTime.of(DAY.plusDays(i), LocalTime.of(9, 0)), 2);
            AttTestEnvironment.flowListener.onBusinessApproved(
                org.dromara.agentoa.workflow.domain.enums.BusinessType.CORRECTION, 30L + i, 910L + i,
                AttTestEnvironment.USER_HR);
        }
        AttTestEnvironment.seedCorrectionRequest(40L, USER, DAY.plusDays(10), 1,
            LocalDateTime.of(DAY.plusDays(10), LocalTime.of(9, 0)), 2);
        assertThatThrownBy(() -> AttTestEnvironment.flowListener.onBusinessValidated(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.CORRECTION, 40L, USER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("每月补卡上限");
    }

    static final int MAX_LIMIT = 3;

    @Test
    void approvedOvertimeCreatesSettledRecord() {
        AttTestEnvironment.seedOvertimeRequest(50L, USER, DAY,
            LocalDateTime.of(DAY, LocalTime.of(18, 30)), LocalDateTime.of(DAY, LocalTime.of(20, 30)), 120, 2);
        AttTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.OVERTIME, 50L, 903L, AttTestEnvironment.USER_HR);
        AttTestEnvironment.flowListener.onBusinessApproved(
            org.dromara.agentoa.workflow.domain.enums.BusinessType.OVERTIME, 50L, 903L, AttTestEnvironment.USER_HR);

        assertThat(AttTestEnvironment.overtimes.selectCount(new LambdaQueryWrapper<OaOvertime>()
            .eq(OaOvertime::getOvertimeRequestId, 50L))).isEqualTo(1);
        OaAttendanceDay day = dayRow(DAY);
        assertThat(day.getOvertimeMinutes()).isEqualTo(120);
        assertThat(day.getIsAbnormal()).isEqualTo(1);
    }
}
