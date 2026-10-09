package org.dromara.agentoa.workflow;

import org.dromara.agentoa.workflow.domain.policy.WorkflowAccessPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowAccessPolicyTest {

    @Test
    void initiatorAndParticipantCanView() {
        assertThat(WorkflowAccessPolicy.canViewInstance(false, false, 1L, 1L, false)).isTrue();
        assertThat(WorkflowAccessPolicy.canViewInstance(false, false, 2L, 1L, true)).isTrue();
        assertThat(WorkflowAccessPolicy.canViewInstance(false, false, 2L, 1L, false)).isFalse();
        assertThat(WorkflowAccessPolicy.canViewInstance(false, false, null, 1L, false)).isFalse();
    }

    @Test
    void superAdminAndMonitorScopeCanViewEverything() {
        assertThat(WorkflowAccessPolicy.canViewInstance(true, false, 9L, 1L, false)).isTrue();
        assertThat(WorkflowAccessPolicy.canViewInstance(false, true, 9L, 1L, false)).isTrue();
    }

    @Test
    void onlyAssigneeOrCandidateMayAct() {
        assertThat(WorkflowAccessPolicy.canActOnTask(true)).isTrue();
        assertThat(WorkflowAccessPolicy.canActOnTask(false)).isFalse();
    }

    @Test
    void revokeRequiresInitiatorAndUntouchedRunningInstance() {
        assertThat(WorkflowAccessPolicy.canRevoke(false, 1L, 1L, false, true)).isTrue();
        assertThat(WorkflowAccessPolicy.canRevoke(false, 2L, 1L, false, true)).isFalse();
        assertThat(WorkflowAccessPolicy.canRevoke(false, 1L, 1L, true, true)).isFalse();
        assertThat(WorkflowAccessPolicy.canRevoke(false, 1L, 1L, false, false)).isFalse();
        assertThat(WorkflowAccessPolicy.canRevoke(true, 9L, 1L, false, true)).isTrue();
    }
}
