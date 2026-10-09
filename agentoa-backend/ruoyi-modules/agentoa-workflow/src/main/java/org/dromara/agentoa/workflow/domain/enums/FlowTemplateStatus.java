package org.dromara.agentoa.workflow.domain.enums;

public enum FlowTemplateStatus {

    DRAFT,
    PUBLISHED,
    RETIRED;

    public static FlowTemplateStatus from(String value) {
        for (FlowTemplateStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知模板状态: " + value);
    }
}
