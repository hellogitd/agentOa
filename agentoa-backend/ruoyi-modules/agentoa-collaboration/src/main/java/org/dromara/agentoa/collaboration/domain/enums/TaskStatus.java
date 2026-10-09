package org.dromara.agentoa.collaboration.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 任务状态（docs/17 第 5 步，字典 cl_task_status）。
 * DONE/CANCELLED 为终态；非法跳转由 {@link #canTransitionTo} 拒绝。
 */
public enum TaskStatus {
    TODO(1), IN_PROGRESS(2), BLOCKED(3), DONE(4), CANCELLED(5);

    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean terminal() {
        return this == DONE || this == CANCELLED;
    }

    /** 合法状态跳转 */
    public boolean canTransitionTo(TaskStatus target) {
        return switch (this) {
            case TODO -> target == IN_PROGRESS || target == BLOCKED || target == DONE || target == CANCELLED;
            case IN_PROGRESS -> target == TODO || target == BLOCKED || target == DONE || target == CANCELLED;
            case BLOCKED -> target == IN_PROGRESS || target == TODO || target == CANCELLED;
            case DONE, CANCELLED -> false;
        };
    }

    public static TaskStatus from(Integer code) {
        for (TaskStatus value : values()) {
            if (value.code == (code == null ? 1 : code)) {
                return value;
            }
        }
        return TODO;
    }

    /** 未完成集合（列表筛选用） */
    public static Set<TaskStatus> openSet() {
        return EnumSet.of(TODO, IN_PROGRESS, BLOCKED);
    }
}
