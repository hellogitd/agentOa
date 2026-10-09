package org.dromara.agentoa.notice.domain.enums;

/**
 * 公告受众范围（docs/15 第 3 步，字典 nc_scope_type）。
 */
public enum ScopeType {

    /** 全员 */
    ALL(1),
    /** 部门（scope_values 为部门 ID 逗号分隔） */
    DEPT(2),
    /** 角色（scope_values 为角色 key 逗号分隔） */
    ROLE(3),
    /** 指定人（scope_values 为用户 ID 逗号分隔） */
    USER(4);

    private final int code;

    ScopeType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ScopeType from(int code) {
        for (ScopeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的公告范围: " + code);
    }
}
