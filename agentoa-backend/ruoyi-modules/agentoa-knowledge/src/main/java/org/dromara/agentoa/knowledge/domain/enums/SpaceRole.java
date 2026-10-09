package org.dromara.agentoa.knowledge.domain.enums;

/**
 * 空间角色（docs/16）：OWNER/EDITOR/COMMENTER/VIEWER，默认无权限。
 */
public enum SpaceRole {

    OWNER("OWNER", 4),
    EDITOR("EDITOR", 3),
    COMMENTER("COMMENTER", 2),
    VIEWER("VIEWER", 1);

    private final String code;

    private final int rank;

    SpaceRole(String code, int rank) {
        this.code = code;
        this.rank = rank;
    }

    public String code() {
        return code;
    }

    public int rank() {
        return rank;
    }

    public static SpaceRole from(String code) {
        for (SpaceRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("非法空间角色: " + code);
    }

    /** 取较高角色（空间角色与文档授权合并） */
    public static SpaceRole max(SpaceRole a, SpaceRole b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.rank >= b.rank ? a : b;
    }
}
