package org.dromara.agentoa.collaboration.domain.enums;

/** 预约状态（字典 cl_booking_status）：取消/释放保留历史 */
public enum BookingStatus {
    BOOKED(1), CHECKED_IN(2), CANCELLED(3), RELEASED(4);

    private final int code;

    BookingStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
