package org.dromara.agentoa.collaboration.domain.enums;

/** 邀请响应状态（oa_calendar_attendee.response_status） */
public enum AttendeeResponse {
    PENDING("PENDING"), ACCEPTED("ACCEPTED"), REJECTED("REJECTED");

    private final String code;

    AttendeeResponse(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AttendeeResponse from(String code) {
        for (AttendeeResponse value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return PENDING;
    }
}
