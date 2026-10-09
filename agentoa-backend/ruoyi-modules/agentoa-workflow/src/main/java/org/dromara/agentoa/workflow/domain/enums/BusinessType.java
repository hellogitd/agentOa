package org.dromara.agentoa.workflow.domain.enums;

import java.util.Locale;

public enum BusinessType {

    LEAVE("leave"),
    OVERTIME("overtime"),
    CORRECTION("correction"),
    REIMBURSE("reimburse"),
    REGULARIZE("regularize"),
    OFFBOARD("offboard"),
    /** 会签演示模板（WF-08，多实例全员同意语义） */
    COUNTERSIGN("countersign"),
    /** 或签演示模板（WF-08，多实例任一同意语义） */
    EITHER_SIGN("either_sign"),
    /**
     * 通用承接（M5）：纯 OA 表单/自定义业务类型统一由 GenericFlowHandler 承接。
     * 未识别的业务类型键归到此值，真正的业务标识以流程定义上的 businessType 为准。
     */
    GENERIC("generic");

    private final String key;

    BusinessType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * 宽松解析：命中枚举返回对应项，否则返回 {@link #GENERIC}。
     * 流程编排按原始业务标识（String）查找定义，此处只用于事件回调分发。
     */
    public static BusinessType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BusinessType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return GENERIC;
    }

    /** 严格解析：未知名直接报错（仅用于确需枚举语义的场景） */
    public static BusinessType fromStrict(String value) {
        if (value == null) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        for (BusinessType type : values()) {
            if (type.key.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知业务类型: " + value);
    }
}
