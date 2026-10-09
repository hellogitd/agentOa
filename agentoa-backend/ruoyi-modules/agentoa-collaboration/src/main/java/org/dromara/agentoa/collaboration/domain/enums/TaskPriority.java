package org.dromara.agentoa.collaboration.domain.enums;

/** 任务优先级（字典 cl_task_priority） */
public enum TaskPriority {
    P0(1), P1(2), P2(3), P3(4);

    private final int code;

    TaskPriority(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TaskPriority from(Integer code) {
        for (TaskPriority value : values()) {
            if (value.code == (code == null ? 2 : code)) {
                return value;
            }
        }
        return P1;
    }
}
