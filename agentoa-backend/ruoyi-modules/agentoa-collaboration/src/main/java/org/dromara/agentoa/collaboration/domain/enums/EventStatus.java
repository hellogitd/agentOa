package org.dromara.agentoa.collaboration.domain.enums;

/** 日程状态（1正常 2已取消） */
public enum EventStatus {
    NORMAL(1), CANCELLED(2);

    private final int code;

    EventStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
