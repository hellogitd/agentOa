package org.dromara.agentoa.collaboration.domain.enums;

/** 任务活动类型（oa_task_activity.activity_type） */
public enum ActivityType {
    CREATE(1), STATUS(2), PROGRESS(3), COMMENT(4), MEMBER(5);

    private final int code;

    ActivityType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
