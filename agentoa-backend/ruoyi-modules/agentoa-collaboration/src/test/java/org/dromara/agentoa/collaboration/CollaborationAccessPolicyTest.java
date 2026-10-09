package org.dromara.agentoa.collaboration;

import org.dromara.agentoa.collaboration.domain.enums.Visibility;
import org.dromara.agentoa.collaboration.domain.policy.CollaborationAccessPolicy;
import org.dromara.agentoa.collaboration.domain.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块 7 对象级权限与任务状态机纯函数（docs/17 测试与退出条件） */
class CollaborationAccessPolicyTest {

    private static final Long ORGANIZER = 1L;
    private static final Long ATTENDEE = 2L;
    private static final Long STRANGER = 3L;
    private static final Long DEPT = 10L;
    private static final Long OTHER_DEPT = 20L;

    @Test
    void privateEventOnlyForOrganizerAndAttendees() {
        assertThat(CollaborationAccessPolicy.canViewEvent(ORGANIZER, ORGANIZER, Set.of(), Visibility.PRIVATE,
            DEPT, DEPT, false)).isTrue();
        assertThat(CollaborationAccessPolicy.canViewEvent(ATTENDEE, ORGANIZER, Set.of(ATTENDEE), Visibility.PRIVATE,
            DEPT, DEPT, false)).isTrue();
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(ATTENDEE), Visibility.PRIVATE,
            DEPT, DEPT, false)).isFalse();
    }

    @Test
    void deptVisibilityLimitedToOrganizerDept() {
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(), Visibility.DEPT,
            DEPT, DEPT, false)).isTrue();
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(), Visibility.DEPT,
            OTHER_DEPT, DEPT, false)).isFalse();
    }

    @Test
    void allVisibilityVisibleToEveryone() {
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(), Visibility.ALL,
            OTHER_DEPT, DEPT, false)).isTrue();
    }

    @Test
    void superAdminSeesEverythingButAttendeeResponseIsNotManagement() {
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(), Visibility.PRIVATE,
            OTHER_DEPT, DEPT, true)).isTrue();
        assertThat(CollaborationAccessPolicy.canViewEvent(STRANGER, ORGANIZER, Set.of(), Visibility.PRIVATE,
            OTHER_DEPT, DEPT, false)).isFalse();
        assertThat(CollaborationAccessPolicy.canManageEvent(ATTENDEE, ORGANIZER, false)).isFalse();
        assertThat(CollaborationAccessPolicy.canManageEvent(ORGANIZER, ORGANIZER, false)).isTrue();
    }

    @Test
    void taskVisibilityCoversAssignerAssigneeAndMembers() {
        assertThat(CollaborationAccessPolicy.canViewTask(1L, 1L, 2L, Set.of())).isTrue();
        assertThat(CollaborationAccessPolicy.canViewTask(2L, 1L, 2L, Set.of())).isTrue();
        assertThat(CollaborationAccessPolicy.canViewTask(3L, 1L, 2L, Set.of(3L))).isTrue();
        assertThat(CollaborationAccessPolicy.canViewTask(4L, 1L, 2L, Set.of(3L))).isFalse();
    }

    @Test
    void taskStateMachineRejectsIllegalJumps() {
        assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE)).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.BLOCKED)).isTrue();
        assertThat(TaskStatus.BLOCKED.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
        assertThat(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO)).isFalse();
        assertThat(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.DONE)).isFalse();
        assertThat(TaskStatus.DONE.terminal()).isTrue();
    }
}
