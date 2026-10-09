package org.dromara.agentoa.reporting;

import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块 8 权限矩阵（docs/18 权限过滤、docs/02 13.3 RP-07）。 */
class ReportingAccessPolicyTest {

    @Test
    void dashboardsRequireReportListPermission() {
        assertThat(ReportingAccessPolicy.canViewDashboards(true, Set.of())).isTrue();
        assertThat(ReportingAccessPolicy.canViewDashboards(false, Set.of(ReportingAccessPolicy.PERM_REPORT_LIST))).isTrue();
        assertThat(ReportingAccessPolicy.canViewDashboards(false, Set.of())).isFalse();
        assertThat(ReportingAccessPolicy.canViewDashboards(false, Set.of("hr:employee:list"))).isFalse();
        assertThat(ReportingAccessPolicy.canViewDashboards(false, null)).isFalse();
    }

    @Test
    void exportRequiresSeparatePermission() {
        assertThat(ReportingAccessPolicy.canExport(true, Set.of())).isTrue();
        assertThat(ReportingAccessPolicy.canExport(false, Set.of(ReportingAccessPolicy.PERM_REPORT_EXPORT))).isTrue();
        assertThat(ReportingAccessPolicy.canExport(false, Set.of(ReportingAccessPolicy.PERM_REPORT_LIST))).isFalse();
        assertThat(ReportingAccessPolicy.canExport(false, Set.of())).isFalse();
    }

    @Test
    void fullOrganizationScopeLimitedToHrFinanceDirector() {
        assertThat(ReportingAccessPolicy.canViewAllDepartments(true, Set.of())).isTrue();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, Set.of("hr"))).isTrue();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, Set.of("finance"))).isTrue();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, Set.of("director"))).isTrue();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, Set.of("dept_manager"))).isFalse();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, Set.of("employee"))).isFalse();
        assertThat(ReportingAccessPolicy.canViewAllDepartments(false, null)).isFalse();
    }

    @Test
    void workbenchManagementUsesDashboardPermission() {
        assertThat(ReportingAccessPolicy.canViewWorkbenchManagement(false, Set.of(ReportingAccessPolicy.PERM_REPORT_LIST)))
            .isTrue();
        assertThat(ReportingAccessPolicy.canViewWorkbenchManagement(false, Set.of())).isFalse();
    }
}
