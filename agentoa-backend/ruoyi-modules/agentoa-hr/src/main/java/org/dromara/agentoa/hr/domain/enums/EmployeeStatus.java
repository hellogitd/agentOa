package org.dromara.agentoa.hr.domain.enums;

/**
 * 员工生命周期状态。所有状态变更必须经过 {@link #canTransitionTo(String)} 校验并写入历史表。
 */
public enum EmployeeStatus {

    /** 草稿，档案已建立未入职 */
    DRAFT,
    /** 试用期 */
    PROBATION,
    /** 正式在职 */
    ACTIVE,
    /** 待离职（流程审批中，预留模块 2） */
    LEAVE_PENDING,
    /** 已离职 */
    LEFT,
    /** 已停用 */
    DISABLED;

    public boolean canTransitionTo(String target) {
        return canTransitionTo(from(target));
    }

    public boolean canTransitionTo(EmployeeStatus target) {
        return switch (this) {
            case DRAFT -> target == PROBATION || target == DISABLED;
            case PROBATION -> target == ACTIVE || target == LEAVE_PENDING || target == LEFT || target == DISABLED;
            case ACTIVE -> target == LEAVE_PENDING || target == LEFT || target == DISABLED;
            case LEAVE_PENDING -> target == ACTIVE || target == LEFT;
            case LEFT, DISABLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == LEFT || this == DISABLED;
    }

    public static EmployeeStatus from(String value) {
        for (EmployeeStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知员工状态: " + value);
    }
}
