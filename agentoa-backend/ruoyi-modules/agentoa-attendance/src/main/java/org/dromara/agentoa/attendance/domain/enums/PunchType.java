package org.dromara.agentoa.attendance.domain.enums;

/**
 * 打卡类型（docs/04 5.3；字典 at_punch_type）。1上班 2下班 3外勤(P1) 4补卡。
 */
public enum PunchType {

    ON_WORK(1),
    OFF_WORK(2),
    FIELD(3),
    CORRECTION(4);

    private final int code;

    PunchType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static boolean isValid(Integer code) {
        return code != null && (code == 1 || code == 2 || code == FIELD.code);
    }

    public static boolean isField(Integer code) {
        return code != null && code == FIELD.code;
    }
}
