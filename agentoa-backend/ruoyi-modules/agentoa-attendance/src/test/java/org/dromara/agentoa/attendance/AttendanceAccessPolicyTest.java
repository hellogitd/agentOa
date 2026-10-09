package org.dromara.agentoa.attendance;

import org.dromara.agentoa.attendance.domain.policy.AttendanceAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 3 权限矩阵（docs/13 权限与规则）。
 */
class AttendanceAccessPolicyTest {

    private static final Set<String> HR = Set.of("at:group:edit", "at:leave:grant", "at:leave:query",
        "at:report:list", "at:report:export");
    private static final Set<String> MANAGER = Set.of("at:report:list", "at:report:export");
    private static final Set<String> EMPLOYEE = Set.of();

    @Test
    void onlyHrMaintainsRules() {
        assertThat(AttendanceAccessPolicy.canMaintainRules(true, EMPLOYEE)).isTrue();
        assertThat(AttendanceAccessPolicy.canMaintainRules(false, HR)).isTrue();
        assertThat(AttendanceAccessPolicy.canMaintainRules(false, MANAGER)).isFalse();
        assertThat(AttendanceAccessPolicy.canMaintainRules(false, EMPLOYEE)).isFalse();
    }

    @Test
    void grantingRequiresExplicitPermission() {
        assertThat(AttendanceAccessPolicy.canGrant(false, HR)).isTrue();
        assertThat(AttendanceAccessPolicy.canGrant(false, MANAGER)).isFalse();
        assertThat(AttendanceAccessPolicy.canGrant(true, EMPLOYEE)).isTrue();
    }

    @Test
    void viewingOthersRequiresQueryOrReportPermission() {
        assertThat(AttendanceAccessPolicy.canViewOthers(false, HR)).isTrue();
        assertThat(AttendanceAccessPolicy.canViewOthers(false, MANAGER)).isTrue();
        assertThat(AttendanceAccessPolicy.canViewOthers(false, EMPLOYEE)).isFalse();
    }

    @Test
    void exportUsesTheSameAuthorizationAsLists() {
        assertThat(AttendanceAccessPolicy.canExport(false, MANAGER)).isTrue();
        assertThat(AttendanceAccessPolicy.canExport(false, Set.of("at:report:list"))).isFalse();
    }

    @Test
    void employeesOnlySeeOwnDays() {
        assertThat(AttendanceAccessPolicy.canViewDay(false, EMPLOYEE, 200L, 200L)).isTrue();
        assertThat(AttendanceAccessPolicy.canViewDay(false, EMPLOYEE, 200L, 300L)).isFalse();
        assertThat(AttendanceAccessPolicy.canViewDay(false, HR, 200L, 300L)).isTrue();
    }
}
