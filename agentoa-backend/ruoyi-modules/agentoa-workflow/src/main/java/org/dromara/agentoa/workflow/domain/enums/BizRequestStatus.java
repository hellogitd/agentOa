package org.dromara.agentoa.workflow.domain.enums;

public enum BizRequestStatus {

    DRAFT(1),
    RUNNING(2),
    APPROVED(3),
    REJECTED(4),
    CANCELLED(7);

    private final int code;

    BizRequestStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean canEdit() {
        return this == DRAFT || this == REJECTED || this == CANCELLED;
    }

    public boolean canSubmit() {
        return canEdit();
    }

    public static BizRequestStatus from(int code) {
        for (BizRequestStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知申请状态: " + code);
    }
}
