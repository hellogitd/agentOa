package org.dromara.agentoa.knowledge.domain.enums;

/**
 * 空间类型（字典 kn_space_type）。
 */
public enum SpaceType {

    /** 公开：全员可见（按 VIEWER） */
    PUBLIC(1),
    /** 私密：仅创建人 */
    PRIVATE(2),
    /** 团队：指定成员可见 */
    TEAM(3);

    private final int code;

    SpaceType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static SpaceType from(int code) {
        for (SpaceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("非法空间类型: " + code);
    }
}
