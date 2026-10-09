package org.dromara.agentoa.workflow.domain.enums;

/**
 * 审批节点签署方式（钉钉式）：
 * SINGLE 单人审批 / COUNTERSIGN 会签（全员同意）/ EITHERSIGN 或签（任一同意）。
 */
public enum FlowSignMode {

    SINGLE,
    COUNTERSIGN,
    EITHERSIGN;

    public static FlowSignMode from(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE;
        }
        for (FlowSignMode mode : values()) {
            if (mode.name().equals(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知签署方式: " + value);
    }
}
