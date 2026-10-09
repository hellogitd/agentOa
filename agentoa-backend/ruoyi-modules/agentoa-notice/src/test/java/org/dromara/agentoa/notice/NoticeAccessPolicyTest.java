package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.domain.policy.NoticeAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 5 权限矩阵（docs/15 权限与业务规则）。
 */
class NoticeAccessPolicyTest {

    private static final Set<String> MANAGER = Set.of("nt:notice:add");
    private static final Set<String> EMPLOYEE = Set.of();

    @Test
    void employeesOnlySeePublishedAudienceNotices() {
        assertThat(NoticeAccessPolicy.canViewNotice(false, EMPLOYEE, 200L, 100L, true, true)).isTrue();
        assertThat(NoticeAccessPolicy.canViewNotice(false, EMPLOYEE, 200L, 100L, false, true)).isFalse();
        assertThat(NoticeAccessPolicy.canViewNotice(false, EMPLOYEE, 200L, 100L, true, false)).isFalse();
        // 发布人可见自己的草稿
        assertThat(NoticeAccessPolicy.canViewNotice(false, EMPLOYEE, 100L, 100L, false, false)).isTrue();
        // 管理者可见全部
        assertThat(NoticeAccessPolicy.canViewNotice(false, MANAGER, 100L, 300L, false, false)).isTrue();
        assertThat(NoticeAccessPolicy.canViewNotice(true, EMPLOYEE, 100L, 300L, false, false)).isTrue();
    }

    @Test
    void readStatusForManagerOrPublisher() {
        assertThat(NoticeAccessPolicy.canReadStatus(false, Set.of("nt:notice:read"), 200L, 100L)).isTrue();
        assertThat(NoticeAccessPolicy.canReadStatus(false, EMPLOYEE, 100L, 100L)).isTrue();
        assertThat(NoticeAccessPolicy.canReadStatus(false, EMPLOYEE, 200L, 100L)).isFalse();
        assertThat(NoticeAccessPolicy.canReadStatus(true, EMPLOYEE, 200L, 100L)).isTrue();
    }

    @Test
    void manageActionsRequireExplicitPermission() {
        assertThat(NoticeAccessPolicy.canManage(false, MANAGER, "nt:notice:add")).isTrue();
        assertThat(NoticeAccessPolicy.canManage(false, MANAGER, "nt:notice:recall")).isFalse();
        assertThat(NoticeAccessPolicy.canManage(true, EMPLOYEE, "nt:notice:recall")).isTrue();
    }
}
