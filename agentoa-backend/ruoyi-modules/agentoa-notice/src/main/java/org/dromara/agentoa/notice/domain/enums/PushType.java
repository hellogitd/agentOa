package org.dromara.agentoa.notice.domain.enums;

/**
 * 定时推送类型（字典 nc_push_type）。
 */
public enum PushType {

    /** 定时公告：到点调用 AnnouncementService.publish（受众物化+outbox 同事务） */
    ANNOUNCEMENT(1),
    /** 模板推送：按模板渲染并按受众写 outbox */
    TEMPLATE(2);

    private final int code;

    PushType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static PushType from(int code) {
        for (PushType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的推送类型: " + code);
    }
}
