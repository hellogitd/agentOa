package org.dromara.agentoa.collaboration.domain.enums;

/** 日程可见范围（docs/17 第 3 步，字典 cl_visibility） */
public enum Visibility {
    /** 私有：仅组织者 */
    PRIVATE(1),
    /** 参与人：组织者与参与人（默认） */
    ATTENDEES(2),
    /** 本部门：组织者部门成员 */
    DEPT(3),
    /** 全员 */
    ALL(4);

    private final int code;

    Visibility(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Visibility from(Integer code) {
        for (Visibility value : values()) {
            if (value.code == (code == null ? 2 : code)) {
                return value;
            }
        }
        return ATTENDEES;
    }
}
