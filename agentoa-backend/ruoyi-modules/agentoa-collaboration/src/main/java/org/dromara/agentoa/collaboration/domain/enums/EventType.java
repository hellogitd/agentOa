package org.dromara.agentoa.collaboration.domain.enums;

/** 日程类型（字典 cl_event_type） */
public enum EventType {
    EVENT(1), MEETING(2);

    private final int code;

    EventType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static EventType from(Integer code) {
        for (EventType value : values()) {
            if (value.code == (code == null ? 1 : code)) {
                return value;
            }
        }
        return EVENT;
    }
}
