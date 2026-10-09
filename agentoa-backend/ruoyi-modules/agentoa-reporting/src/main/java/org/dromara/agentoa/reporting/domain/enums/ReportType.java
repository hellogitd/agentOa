package org.dromara.agentoa.reporting.domain.enums;

/** 报表类型（看板与导出共用，见 docs/05 第 10 节）。 */
public enum ReportType {

    HR("hr", "人事看板"),
    ATTENDANCE("attendance", "考勤看板"),
    FINANCE("finance", "财务看板"),
    FLOW("flow", "流程看板");

    private final String code;
    private final String label;

    ReportType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ReportType of(String code) {
        for (ReportType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
