package org.dromara.agentoa.notice.domain.enums;

/**
 * 公告生命周期（docs/15 第 2 步，字典 nc_notice_status）。
 */
public enum AnnouncementStatus {

    /** 草稿：可编辑/删除 */
    DRAFT(1),
    /** 已发布：内容冻结，可撤回 */
    PUBLISHED(2),
    /** 已撤回：受众与已读记录保留 */
    RECALLED(3),
    /** 已归档（P1） */
    ARCHIVED(4);

    private final int code;

    AnnouncementStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
