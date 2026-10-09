package org.dromara.agentoa.collaboration.domain.enums;

/** 会议室状态（字典 cl_room_status） */
public enum RoomStatus {
    AVAILABLE(1), MAINTENANCE(2);

    private final int code;

    RoomStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
