package org.dromara.agentoa.workflow.domain.enums;

public enum FlowAction {

    AGREE("agree"),
    REJECT("reject"),
    TRANSFER("transfer"),
    CANCEL("cancel"),
    RETURN("return"),
    ADDSIGN("addsign"),
    URGE("urge"),
    CC("cc"),
    SUSPEND("suspend"),
    RESUME("resume"),
    TERMINATE("terminate");

    private final String key;

    FlowAction(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static FlowAction from(String value) {
        for (FlowAction action : values()) {
            if (action.key.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知任务动作: " + value);
    }
}
