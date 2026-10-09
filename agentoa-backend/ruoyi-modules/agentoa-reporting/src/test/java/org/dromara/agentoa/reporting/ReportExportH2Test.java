package org.dromara.agentoa.reporting;

import org.dromara.agentoa.reporting.domain.bo.ExportCreateBo;
import org.dromara.agentoa.reporting.domain.bo.ExportFilters;
import org.dromara.agentoa.reporting.domain.enums.ExportFormat;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.vo.ExportVo;
import org.dromara.agentoa.reporting.service.support.ReportTable;
import org.dromara.agentoa.reporting.support.ReportingTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导出测试（docs/18 步骤 4 与退出条件）：幂等键、上限、私有文件、操作者/过滤/口径版本留痕、
 * 敏感字段脱敏与报表版本兼容。
 */
class ReportExportH2Test {

    private static final Set<String> EXPORT_PERMS = Set.of(
        org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy.PERM_REPORT_EXPORT);

    @BeforeAll
    static void boot() {
        ReportingTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        ReportingTestEnvironment.clearData();
        ReportingTestEnvironment.logout();
        ReportingTestEnvironment.seedDept(ReportingTestEnvironment.DEPT_A, "研发部");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_EMPLOYEE_A, ReportingTestEnvironment.DEPT_A, "员工甲");
        ReportingTestEnvironment.seedUser(ReportingTestEnvironment.USER_EMPLOYEE_B, ReportingTestEnvironment.DEPT_A, "员工乙");
        ReportingTestEnvironment.seedAllMetrics();
        ReportingTestEnvironment.seedEmployee(1, ReportingTestEnvironment.USER_EMPLOYEE_A, "E001",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", LocalDate.now(), null);
    }

    private void loginOwner() {
        ReportingTestEnvironment.loginAs(ReportingTestEnvironment.USER_EMPLOYEE_A, ReportingTestEnvironment.DEPT_A,
            EXPORT_PERMS, Set.of("hr"));
    }

    private void loginOther() {
        ReportingTestEnvironment.loginAs(ReportingTestEnvironment.USER_EMPLOYEE_B, ReportingTestEnvironment.DEPT_A,
            Set.of(), Set.of("employee"));
    }

    private ExportCreateBo hrBo() {
        ExportCreateBo bo = new ExportCreateBo();
        bo.setReportType("hr");
        bo.setFormat("CSV");
        return bo;
    }

    @Test
    void createRequiresIdempotencyKey() {
        loginOwner();
        assertThatThrownBy(() -> ReportingTestEnvironment.exportService.create(hrBo(), null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("RP_IDEMPOTENCY_KEY");
        assertThat(ReportingTestEnvironment.exportCount()).isZero();
    }

    @Test
    void sameKeySameRequestReplaysFirstResult() {
        loginOwner();
        ExportVo first = ReportingTestEnvironment.exportService.create(hrBo(), "key-1");
        ExportVo replay = ReportingTestEnvironment.exportService.create(hrBo(), "key-1");
        assertThat(replay.getExportNo()).isEqualTo(first.getExportNo());
        assertThat(ReportingTestEnvironment.exportCount()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentRequestConflicts() {
        loginOwner();
        ReportingTestEnvironment.exportService.create(hrBo(), "key-1");
        ExportCreateBo other = hrBo();
        other.setReportType("finance");
        assertThatThrownBy(() -> ReportingTestEnvironment.exportService.create(other, "key-1"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void pendingTaskRunsToPrivateFileAndKeepsAudit() {
        loginOwner();
        ExportVo task = ReportingTestEnvironment.exportService.create(hrBo(), "key-2");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getMetricVersion()).isEqualTo(1);
        assertThat(task.getRequestedBy()).isEqualTo(ReportingTestEnvironment.USER_EMPLOYEE_A);

        ReportingTestEnvironment.exportService.processPending();

        ExportVo done = ReportingTestEnvironment.exportService.get(task.getId());
        assertThat(done.getStatus()).isEqualTo("SUCCESS");
        assertThat(done.getRowCount()).isEqualTo(1);
        assertThat(done.getFileId()).isNotNull();
        assertThat(done.getFinishTime()).isNotBlank();
        assertThat(done.getFilters()).contains("\"scope\"");

        byte[] content = ReportingTestEnvironment.STORED_FILES.get(done.getFileId());
        assertThat(content).isNotNull();
        String csv = new String(content, StandardCharsets.UTF_8);
        assertThat(csv).contains("工号").contains("姓名").contains("手机号");
        assertThat(csv).contains("138****0001");
        assertThat(csv).doesNotContain("13800000001");
    }

    @Test
    void failedTaskKeepsErrorMessage() {
        loginOwner();
        ExportVo task = ReportingTestEnvironment.exportService.create(hrBo(), "key-3");
        ReportingTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_report_export SET filters = ? WHERE id = ?", "{bad-json", task.getId());
        ReportingTestEnvironment.exportService.processPending();
        ExportVo done = ReportingTestEnvironment.exportService.get(task.getId());
        assertThat(done.getStatus()).isEqualTo("FAILED");
        assertThat(done.getErrorMessage()).isNotBlank();
    }

    @Test
    void exportCapRejectsOversizedTables() {
        ReportingTestEnvironment.seedEmployee(2, ReportingTestEnvironment.USER_EMPLOYEE_B, "E002",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", LocalDate.now(), null);
        ReportingTestEnvironment.seedEmployee(3, null, "E003",
            ReportingTestEnvironment.DEPT_A, "ACTIVE", LocalDate.now(), null);
        loginOwner();
        assertThatThrownBy(() -> ReportingTestEnvironment.queryService.table(ReportType.HR,
            org.dromara.agentoa.reporting.service.support.ReportRange.currentMonth(), null, 2))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("RP_EXPORT_LIMIT");
    }

    @Test
    void syncExportRecordsOperatorFiltersAndVersion() {
        loginOwner();
        ExportFilters filters = new ExportFilters();
        filters.setStartDate(LocalDate.now().minusDays(7).toString());
        filters.setEndDate(LocalDate.now().toString());
        ReportTable table = ReportingTestEnvironment.exportService.syncExport(ReportType.HR,
            ExportFormat.XLSX, filters);
        assertThat(table.rows()).hasSize(1);
        assertThat(ReportingTestEnvironment.exportCount()).isEqualTo(1);
        var row = ReportingTestEnvironment.exportRow(ReportingTestEnvironment.exports
            .selectList(null).get(0).getId());
        assertThat(String.valueOf(row.get("status"))).isEqualTo("SUCCESS");
        assertThat(String.valueOf(row.get("format"))).isEqualTo("XLSX");
        assertThat(String.valueOf(row.get("metric_version"))).isEqualTo("1");
        assertThat(String.valueOf(row.get("filters"))).contains("scope");
        assertThat(row.get("requested_by").toString())
            .isEqualTo(String.valueOf(ReportingTestEnvironment.USER_EMPLOYEE_A));
    }

    @Test
    void versionSnapshotStaysWithEachRecord() {
        loginOwner();
        ExportVo oldTask = ReportingTestEnvironment.exportService.create(hrBo(), "key-v1");
        ReportingTestEnvironment.jdbcTemplate().update(
            "INSERT INTO oa_report_metric_version(id, metric_code, version, definition, status) VALUES(?,?,?,?,?)",
            9901L, "hr.active_count", 2, "{\"statusFilter\":\"active+probation\"}", 1);
        ReportingTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_report_metric SET current_version = 2 WHERE metric_code = 'hr.active_count'");

        ExportVo newTask = ReportingTestEnvironment.exportService.create(hrBo(), "key-v2");
        assertThat(newTask.getMetricVersion()).isEqualTo(2);
        assertThat(ReportingTestEnvironment.exportService.get(oldTask.getId()).getMetricVersion()).isEqualTo(1);
    }

    @Test
    void recordsArePrivateToTheirOwnerUnlessExportAdmin() {
        loginOwner();
        ExportVo task = ReportingTestEnvironment.exportService.create(hrBo(), "key-4");
        loginOther();
        assertThatThrownBy(() -> ReportingTestEnvironment.exportService.get(task.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("RP_EXPORT_NOT_FOUND");
        assertThat(ReportingTestEnvironment.exportService.list(new org.dromara.agentoa.reporting.domain.bo.ExportPageQuery())
            .getRecords()).isEmpty();
    }
}
