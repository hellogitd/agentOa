package org.dromara.agentoa.knowledge.domain.enums;

/**
 * 文档状态（字典 kn_doc_status）：草稿、已发布、已归档；回收站由 deleted_at 单独标记。
 */
public enum DocumentStatus {

    DRAFT(1),
    PUBLISHED(2),
    ARCHIVED(3);

    private final int code;

    DocumentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DocumentStatus from(int code) {
        for (DocumentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法文档状态: " + code);
    }
}
