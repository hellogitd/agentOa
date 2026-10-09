package org.dromara.agentoa.attendance.domain.enums;

/**
 * 考勤日主状态（字典 at_work_status）。迟到、早退、缺卡是独立计算结果，不用互斥枚举覆盖。
 */
public enum DayStatus {

    PENDING(0),
    PRESENT(1),
    REST(2),
    ABSENT(3),
    LEAVE(4);

    private final int code;

    DayStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
