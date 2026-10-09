package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 1 角色矩阵：员工、部门经理、HR、管理员。
 */
class HrAccessPolicyTest {

    private static final Set<String> SENSITIVE = Set.of(HrAccessPolicy.PERM_VIEW_SENSITIVE);

    @Test
    void employeeSeesSelfAndOwnDepartmentOnly() {
        assertThat(HrAccessPolicy.canView(false, false, 200L, 10L, 200L, 10L, 100L)).isTrue();
        assertThat(HrAccessPolicy.canView(false, false, 200L, 10L, 300L, 10L, 100L)).isTrue();
        assertThat(HrAccessPolicy.canView(false, false, 200L, 10L, 300L, 20L, 400L)).isFalse();
    }

    @Test
    void managerSeesOwnDepartmentOrDirectReports() {
        assertThat(HrAccessPolicy.canView(false, false, 100L, 10L, 200L, 10L, 100L)).isTrue();
        assertThat(HrAccessPolicy.canView(false, false, 100L, 10L, 300L, 20L, 100L)).isTrue();
        assertThat(HrAccessPolicy.canView(false, false, 100L, 10L, 300L, 20L, 400L)).isFalse();
    }

    @Test
    void hrAndAdminSeeEveryone() {
        assertThat(HrAccessPolicy.canView(false, true, 100L, 10L, 300L, 20L, 400L)).isTrue();
        assertThat(HrAccessPolicy.canView(true, false, 1L, null, 300L, 20L, 400L)).isTrue();
    }

    @Test
    void onlyHrAndAdminEditAndManageLifecycle() {
        assertThat(HrAccessPolicy.canEdit(false, true)).isTrue();
        assertThat(HrAccessPolicy.canEdit(true, false)).isTrue();
        assertThat(HrAccessPolicy.canEdit(false, false)).isFalse();

        assertThat(HrAccessPolicy.canManageLifecycle(false, true)).isTrue();
        assertThat(HrAccessPolicy.canManageLifecycle(false, false)).isFalse();
        assertThat(HrAccessPolicy.canImportExport(false, true)).isTrue();
        assertThat(HrAccessPolicy.canImportExport(false, false)).isFalse();
    }

    @Test
    void selfServiceOnlyForSelf() {
        assertThat(HrAccessPolicy.canEditSelfService(false, false, 200L, 200L)).isTrue();
        assertThat(HrAccessPolicy.canEditSelfService(false, false, 200L, 300L)).isFalse();
        assertThat(HrAccessPolicy.canEditSelfService(false, true, 100L, 300L)).isTrue();
    }

    @Test
    void sensitiveFieldsNeedExplicitGrant() {
        assertThat(HrAccessPolicy.canViewSensitive(true, Set.of())).isTrue();
        assertThat(HrAccessPolicy.canViewSensitive(false, SENSITIVE)).isTrue();
        assertThat(HrAccessPolicy.canViewSensitive(false, Set.of("hr:employee:list"))).isFalse();
        assertThat(HrAccessPolicy.canViewSensitive(false, null)).isFalse();
    }
}
