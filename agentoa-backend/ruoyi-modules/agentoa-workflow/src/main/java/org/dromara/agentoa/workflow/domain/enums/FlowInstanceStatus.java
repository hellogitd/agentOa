package org.dromara.agentoa.workflow.domain.enums;

public enum FlowInstanceStatus {

    RUNNING(1),
    APPROVED(2),
    REJECTED(3),
    REVOKED(4),
    SUSPENDED(5),
    TERMINATED(6);

    private final int code;

    FlowInstanceStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isFinished() {
        return this != RUNNING && this != SUSPENDED;
    }

    public static FlowInstanceStatus from(int code) {
        for (FlowInstanceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知流程状态: " + code);
    }
}
