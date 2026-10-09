package org.dromara.agentoa.reporting;

import org.dromara.agentoa.reporting.domain.bo.DetailQueryBo;
import org.dromara.agentoa.reporting.domain.bo.ReportRangeBo;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.domain.vo.AttendanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.DetailTableVo;
import org.dromara.agentoa.reporting.domain.vo.FinanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.FlowStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.HrStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.LabelValueVo;
import org.dromara.agentoa.reporting.domain.vo.WorkbenchVo;
import org.dromara.agentoa.reporting.support.ReportingTestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看板口径测试（docs/18 退出条件）：列表与报表对账、部门权限、时间边界、撤销/拒绝状态过滤、
 * 敏感字段脱敏、工作台关键卡片与口径版本。
 */
class ReportStatisticsH2Test {

    private static final Set<String> REPORT_PERMS = Set.of(ReportingAccessPolicy.PERM_REPORT_LIST);

    @BeforeAll
    static void boot() {
        ReportingTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        ReportingTestEnvironment.clearData();
        ReportingTestEnvironment.logout();
        ReportingTestEnvironment.seedDept(ReportingTestEnvironment.DEPT_A, "研发部");
        ReportingTestEnvironment.seedDept(ReportingTestEnvironment.DEPT_B, "市场部");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_EMPLOYEE_A, ReportingTestEnvironment.DEPT_A, "员工甲");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_MANAGER_A, ReportingTestEnvironment.DEPT_A, "经理甲");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_HR, ReportingTestEnvironment.DEPT_A, "人事");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_EMPLOYEE_B, ReportingTestEnvironment.DEPT_B, "员工乙");
        ReportingTestEnvironment.seedAllMetrics();
    }

    private void loginHr() {
        ReportingTestEnvironment.loginAs(ReportingTestEnvironment.USER_HR, ReportingTestEnvironment.DEPT_A,
            REPORT_PERMS, Set.of("hr"));
    }

    private void loginManager() {
        ReportingTestEnvironment.loginAs(ReportingTestEnvironment.USER_MANAGER_A, ReportingTestEnvironment.DEPT_A,
            REPORT_PERMS, Set.of("dept_manager"));
    }

    private void loginEmployee() {
        ReportingTestEnvironment.loginAs(ReportingTestEnvironment.USER_EMPLOYEE_A, ReportingTestEnvironment.DEPT_A,
            Set.of(), Set.of("employee"));
    }

    @Test
    void hrCardsReconcileWithRosterAndRespectDeptScope() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedEmployee(1, ReportingTestEnvironment.USER_EMPLOYEE_A, "E001",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", today.minusYears(2), null);
        ReportingTestEnvironment.seedEmployee(2, ReportingTestEnvironment.USER_MANAGER_A, "E002",
            ReportingTestEnvironment.DEPT_A, "PROBATION", today, null);
        ReportingTestEnvironment.seedEmployee(3, ReportingTestEnvironment.USER_HR, "E003",
            ReportingTestEnvironment.DEPT_A, "LEFT", today.minusYears(1), today);
        ReportingTestEnvironment.seedEmployee(4, ReportingTestEnvironment.USER_EMPLOYEE_B, "E004",
            ReportingTestEnvironment.DEPT_B, "ACTIVE", today, null);

        loginHr();
        HrStatisticsVo all = ReportingTestEnvironment.queryService.hr(new ReportRangeBo());
        assertThat(card(all.getCards(), "hr.active_count")).isEqualTo("3");
        assertThat(card(all.getCards(), "hr.new_hire_month")).isEqualTo("2");
        assertThat(card(all.getCards(), "hr.leave_month")).isEqualTo("1");
        assertThat(card(all.getCards(), "hr.probation_count")).isEqualTo("1");
        assertThat(sum(all.getDeptDistribution())).isEqualByComparingTo("3");
        assertThat(sumTrend(all.getTrend12m())).isEqualByComparingTo("2");

        loginManager();
        HrStatisticsVo ownDept = ReportingTestEnvironment.queryService.hr(new ReportRangeBo());
        assertThat(card(ownDept.getCards(), "hr.active_count")).isEqualTo("2");
        assertThat(sum(ownDept.getDeptDistribution())).isEqualByComparingTo("2");
        assertThat(ownDept.getDeptDistribution()).extracting(LabelValueVo::getLabel)
            .containsExactly("研发部");
    }

    @Test
    void rangeBoundariesAreInclusiveOnBothEnds() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedEmployee(1, ReportingTestEnvironment.USER_EMPLOYEE_A, "E001",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", today.minusDays(1), null);
        ReportingTestEnvironment.seedEmployee(2, ReportingTestEnvironment.USER_MANAGER_A, "E002",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", today, null);
        ReportingTestEnvironment.seedEmployee(3, ReportingTestEnvironment.USER_HR, "E003",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", today.plusDays(1), null);

        loginHr();
        ReportRangeBo range = new ReportRangeBo();
        range.setStartDate(today.minusDays(1));
        range.setEndDate(today);
        HrStatisticsVo vo = ReportingTestEnvironment.queryService.hr(range);
        assertThat(card(vo.getCards(), "hr.new_hire_month")).isEqualTo("2");

        range.setEndDate(today.plusDays(1));
        HrStatisticsVo wider = ReportingTestEnvironment.queryService.hr(range);
        assertThat(card(wider.getCards(), "hr.new_hire_month")).isEqualTo("3");
    }

    @Test
    void attendanceRateMatchesDayRowsAndDeptScope() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedAttendanceDay(10, ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, today, 480, 400, 30, 0, 60, false);
        ReportingTestEnvironment.seedAttendanceDay(11, ReportingTestEnvironment.USER_MANAGER_A,
            ReportingTestEnvironment.DEPT_A, today, 480, 480, 0, 0, 0, true);
        ReportingTestEnvironment.seedAttendanceDay(12, ReportingTestEnvironment.USER_EMPLOYEE_B,
            ReportingTestEnvironment.DEPT_B, today, 480, 480, 0, 0, 0, false);

        loginHr();
        AttendanceStatisticsVo all = ReportingTestEnvironment.queryService.attendance(new ReportRangeBo());
        assertThat(card(all.getCards(), "att.rate_today")).isEqualTo("0.9444");
        assertThat(card(all.getCards(), "att.late_week")).isEqualTo("1");
        assertThat(card(all.getCards(), "att.overtime_month")).isEqualTo("60");
        assertThat(all.getExceptions()).hasSize(1);
        assertThat(all.getExceptions().get(0).getDetail()).isEqualTo("缺卡");

        loginManager();
        AttendanceStatisticsVo own = ReportingTestEnvironment.queryService.attendance(new ReportRangeBo());
        assertThat(card(own.getCards(), "att.rate_today")).isEqualTo("0.9167");
        assertThat(card(own.getCards(), "att.overtime_month")).isEqualTo("60");
    }

    @Test
    void attendanceDetailsUseTheSameRangeAsCards() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedAttendanceDay(10, ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, today.minusDays(1), 480, 480, 0, 0, 0, false);
        ReportingTestEnvironment.seedAttendanceDay(11, ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, today, 480, 480, 0, 0, 0, false);
        ReportingTestEnvironment.seedAttendanceDay(12, ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, today.plusDays(1), 480, 480, 0, 0, 0, false);

        loginHr();
        DetailQueryBo query = new DetailQueryBo();
        query.setStartDate(today.minusDays(1));
        query.setEndDate(today);
        DetailTableVo table = ReportingTestEnvironment.queryService.details("attendance", query);
        assertThat(table.getTotal()).isEqualTo(2);
        assertThat(table.getRows()).hasSize(2);
    }

    @Test
    void rejectedAndRevokedRowsAreExcludedFromApprovedMetrics() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedFlowInstance(101, "reimburse", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 2, today.minusDays(2).atTime(10, 0), today.minusDays(1).atTime(12, 0),
            26L * 3600000, "报销-101");
        ReportingTestEnvironment.seedFlowInstance(102, "reimburse", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 3, today.atTime(9, 0), today.atTime(10, 0), 3600000L, "报销-102");
        ReportingTestEnvironment.seedFlowInstance(103, "reimburse", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 4, today.atTime(11, 0), today.atTime(12, 0), 3600000L, "报销-103");
        ReportingTestEnvironment.seedReimburse(201, "R2026001", ReportingTestEnvironment.USER_EMPLOYEE_A, 6, 101L, "300.00");
        ReportingTestEnvironment.seedReimburse(202, "R2026002", ReportingTestEnvironment.USER_EMPLOYEE_A, 4, 102L, "500.00");
        ReportingTestEnvironment.seedReimburse(203, "R2026003", ReportingTestEnvironment.USER_EMPLOYEE_A, 7, 103L, "700.00");
        ReportingTestEnvironment.seedReimburse(204, "R2026004", ReportingTestEnvironment.USER_EMPLOYEE_A, 2, null, "150.00");
        ReportingTestEnvironment.seedExpenseItem(301, 201L, "travel", today.minusDays(3), "200.00");
        ReportingTestEnvironment.seedExpenseItem(302, 201L, "meal", today.minusDays(3), "100.00");
        ReportingTestEnvironment.seedExpenseItem(303, 202L, "travel", today.minusDays(3), "900.00");
        ReportingTestEnvironment.seedPayment(401, 201L, "300.00", today.minusDays(1), 2);

        ReportingTestEnvironment.seedLeaveRequest(501, ReportingTestEnvironment.USER_EMPLOYEE_A, "sick", 3,
            today.atTime(9, 0), 480);
        ReportingTestEnvironment.seedLeaveRequest(502, ReportingTestEnvironment.USER_EMPLOYEE_A, "annual", 4,
            today.atTime(9, 0), 480);
        ReportingTestEnvironment.seedLeaveRequest(503, ReportingTestEnvironment.USER_EMPLOYEE_A, "personal", 7,
            today.atTime(9, 0), 480);

        loginHr();
        ReportRangeBo range = wideRange();
        FinanceStatisticsVo finance = ReportingTestEnvironment.queryService.finance(range);
        assertThat(new BigDecimal(card(finance.getCards(), "fn.approved_month"))).isEqualByComparingTo("300.00");
        assertThat(new BigDecimal(card(finance.getCards(), "fn.paid_month"))).isEqualByComparingTo("300.00");
        assertThat(new BigDecimal(card(finance.getCards(), "fn.pending_amount"))).isEqualByComparingTo("150.00");
        assertThat(new BigDecimal(card(finance.getCards(), "fn.applied_month"))).isEqualByComparingTo("1500.00");
        assertThat(sum(finance.getTypeDistribution())).isEqualByComparingTo("300.00");

        AttendanceStatisticsVo attendance = ReportingTestEnvironment.queryService.attendance(range);
        assertThat(attendance.getLeaveTypeDistribution()).hasSize(1);
        assertThat(attendance.getLeaveTypeDistribution().get(0).getLabel()).isEqualTo("sick");

        FlowStatisticsVo flow = ReportingTestEnvironment.queryService.flow(range);
        assertThat(card(flow.getCards(), "flow.avg_duration_hours")).isEqualTo("26.00");
    }

    @Test
    void flowCardsMatchTodoListSemantics() {
        LocalDateTime now = LocalDateTime.now();
        ReportingTestEnvironment.seedFlowInstance(101, "leave", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 1, now.minusHours(2), null, null, "请假-101");
        ReportingTestEnvironment.seedTodoTask("T1", "P101", ReportingTestEnvironment.USER_EMPLOYEE_A);
        ReportingTestEnvironment.seedCandidateTask("T2", "P101", ReportingTestEnvironment.USER_EMPLOYEE_A);
        ReportingTestEnvironment.seedHistoricTask("H1", "P101", ReportingTestEnvironment.USER_EMPLOYEE_A,
            LocalDateTime.now());
        ReportingTestEnvironment.seedFlowInstance(102, "leave", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 1, now.minusHours(2), null, null, "请假-102");

        loginEmployee();
        WorkbenchVo workbench = ReportingTestEnvironment.queryService.workbench();
        assertThat(workbench.getTodoCount()).isEqualTo(2);
        assertThat(workbench.getTodoRecent()).hasSize(2);
        assertThat(workbench.getInitiatedCount()).isEqualTo(2);

        FlowStatisticsVo flow = ReportingTestEnvironment.queryService.flow(new ReportRangeBo());
        assertThat(card(flow.getCards(), "flow.todo_count")).isEqualTo("2");
        assertThat(card(flow.getCards(), "flow.handled_week")).isEqualTo("1");
    }

    @Test
    void timeoutCountsOnlyStaleInFlightInstances() {
        LocalDateTime now = LocalDateTime.now();
        ReportingTestEnvironment.seedFlowInstance(101, "leave", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 1, now.minusHours(48), null, null, "超时单");
        ReportingTestEnvironment.seedFlowInstance(102, "leave", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 1, now.minusHours(1), null, null, "新鲜单");
        ReportingTestEnvironment.seedFlowInstance(103, "leave", ReportingTestEnvironment.USER_EMPLOYEE_A,
            ReportingTestEnvironment.DEPT_A, 3, now.minusHours(48), now.minusHours(40), 8L * 3600000, "已拒绝");

        loginEmployee();
        FlowStatisticsVo flow = ReportingTestEnvironment.queryService.flow(new ReportRangeBo());
        assertThat(card(flow.getCards(), "flow.timeout_count")).isEqualTo("1");
        assertThat(flow.getTimeouts()).hasSize(1);
        assertThat(flow.getTimeouts().get(0).getLabel()).isEqualTo("超时单");
    }

    @Test
    void workbenchShowsOnlyOwnVisibleSignals() {
        LocalDate today = LocalDate.now();
        ReportingTestEnvironment.seedPunch(1, ReportingTestEnvironment.USER_EMPLOYEE_A, today, today.atTime(9, 0), 1);
        ReportingTestEnvironment.seedPunch(2, ReportingTestEnvironment.USER_EMPLOYEE_A, today, today.atTime(18, 0), 2);
        ReportingTestEnvironment.seedBalance(1, ReportingTestEnvironment.USER_EMPLOYEE_A, today.getYear(),
            "annual", 2400, 600, 1200);
        ReportingTestEnvironment.seedAnnouncement(1, "全员公告", 2, today.atTime(8, 0),
            ReportingTestEnvironment.USER_EMPLOYEE_A);
        ReportingTestEnvironment.seedAnnouncement(2, "他人公告", 2, today.atTime(8, 0),
            ReportingTestEnvironment.USER_EMPLOYEE_B);
        ReportingTestEnvironment.seedAnnouncement(3, "已撤回公告", 3, today.atTime(8, 0),
            ReportingTestEnvironment.USER_EMPLOYEE_A);
        ReportingTestEnvironment.seedEvent(1, ReportingTestEnvironment.USER_EMPLOYEE_A,
            today.atTime(14, 0), today.atTime(15, 0));

        loginEmployee();
        WorkbenchVo workbench = ReportingTestEnvironment.queryService.workbench();
        assertThat(workbench.getAttendance().getTodayPunched()).isTrue();
        assertThat(workbench.getAttendance().getPunchInTime()).contains("09:00");
        assertThat(workbench.getAttendance().getPunchOutTime()).contains("18:00");
        assertThat(workbench.getNotices()).hasSize(1);
        assertThat(workbench.getNotices().get(0).getTitle()).isEqualTo("全员公告");
        assertThat(workbench.getTodayEvents()).hasSize(1);
        assertThat(workbench.getLeaveBalances()).hasSize(1);
        assertThat(workbench.getLeaveBalances().get(0).getAvailableMinutes()).isEqualTo(600);
        assertThat(workbench.getManagement()).isNull();
        assertThat(workbench.getQuickLinks()).isNotEmpty();

        loginHr();
        assertThat(ReportingTestEnvironment.queryService.workbench().getManagement()).isNotNull();
    }

    @Test
    void hrDetailsMaskSensitiveFields() {
        ReportingTestEnvironment.seedEmployee(1, ReportingTestEnvironment.USER_EMPLOYEE_A, "E001",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", LocalDate.now(), null);
        loginHr();
        DetailTableVo table = ReportingTestEnvironment.queryService.details("hr", new DetailQueryBo());
        assertThat(table.getTotal()).isEqualTo(1);
        String phone = String.valueOf(table.getRows().get(0).get("phone"));
        String idCard = String.valueOf(table.getRows().get(0).get("id_card_no"));
        assertThat(phone).contains("****").doesNotContain("13800000001");
        assertThat(idCard).contains("**********").doesNotContain("110101199001010001");
    }

    @Test
    void metricsExposeVersionedDefinitions() {
        loginHr();
        var metrics = ReportingTestEnvironment.metricService.metrics(null);
        assertThat(metrics).hasSize(18);
        assertThat(metrics).allSatisfy(metric -> {
            assertThat(metric.getCurrentVersion()).isGreaterThanOrEqualTo(1);
            assertThat(metric.getDefinition()).isNotBlank();
        });
        assertThat(ReportingTestEnvironment.metricService.currentVersion(
            org.dromara.agentoa.reporting.domain.enums.ReportType.HR)).isEqualTo(1);
    }

    private static String card(List<org.dromara.agentoa.reporting.domain.vo.StatCardVo> cards, String code) {
        return cards.stream().filter(card -> code.equals(card.getCode())).findFirst().orElseThrow().getValue();
    }

    private static BigDecimal sum(List<LabelValueVo> rows) {
        return rows.stream().map(row -> row.getValue() == null ? BigDecimal.ZERO : row.getValue())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumTrend(List<org.dromara.agentoa.reporting.domain.vo.TrendPointVo> rows) {
        return rows.stream().map(row -> row.getValue() == null ? BigDecimal.ZERO : row.getValue())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ReportRangeBo wideRange() {
        LocalDate today = LocalDate.now();
        ReportRangeBo range = new ReportRangeBo();
        range.setStartDate(today.minusDays(7));
        range.setEndDate(today.plusDays(2));
        return range;
    }
}
