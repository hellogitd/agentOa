package org.dromara.agentoa.notice.domain.enums;

/**
 * 定时推送调度方式（字典 nc_schedule_type）。
 */
public enum ScheduleType {

    /** 单次：run_at 执行一次 */
    ONCE(1),
    /** 周期：cron 受限子集（5 字段：分 时 日 月 周） */
    CRON(2);

    private final int code;

    ScheduleType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ScheduleType from(int code) {
        for (ScheduleType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的调度方式: " + code);
    }
}
