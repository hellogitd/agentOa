package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.enums.EmployeeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 员工状态机：正常流转与非法流转。
 */
class EmployeeStatusTest {

    @Test
    void lifecycleHappyPath() {
        assertThat(EmployeeStatus.DRAFT.canTransitionTo(EmployeeStatus.PROBATION)).isTrue();
        assertThat(EmployeeStatus.PROBATION.canTransitionTo(EmployeeStatus.ACTIVE)).isTrue();
        assertThat(EmployeeStatus.ACTIVE.canTransitionTo(EmployeeStatus.LEFT)).isTrue();
    }

    @Test
    void leavePendingBranch() {
        assertThat(EmployeeStatus.ACTIVE.canTransitionTo(EmployeeStatus.LEAVE_PENDING)).isTrue();
        assertThat(EmployeeStatus.LEAVE_PENDING.canTransitionTo(EmployeeStatus.LEFT)).isTrue();
        assertThat(EmployeeStatus.LEAVE_PENDING.canTransitionTo(EmployeeStatus.ACTIVE)).isTrue();
        assertThat(EmployeeStatus.DRAFT.canTransitionTo(EmployeeStatus.LEAVE_PENDING)).isFalse();
    }

    @Test
    void terminalStatesRejectEverything() {
        for (EmployeeStatus target : EmployeeStatus.values()) {
            assertThat(EmployeeStatus.LEFT.canTransitionTo(target)).isFalse();
            assertThat(EmployeeStatus.DISABLED.canTransitionTo(target)).isFalse();
        }
        assertThat(EmployeeStatus.LEFT.isTerminal()).isTrue();
        assertThat(EmployeeStatus.DISABLED.isTerminal()).isTrue();
    }

    @Test
    void invalidJumpsRejected() {
        assertThat(EmployeeStatus.DRAFT.canTransitionTo(EmployeeStatus.ACTIVE)).isFalse();
        assertThat(EmployeeStatus.DRAFT.canTransitionTo(EmployeeStatus.LEFT)).isFalse();
        assertThat(EmployeeStatus.PROBATION.canTransitionTo(EmployeeStatus.DRAFT)).isFalse();
    }

    @Test
    void parseAndUnknown() {
        assertThat(EmployeeStatus.from("ACTIVE")).isEqualTo(EmployeeStatus.ACTIVE);
        assertThatThrownBy(() -> EmployeeStatus.from("UNKNOWN")).isInstanceOf(IllegalArgumentException.class);
    }
}
