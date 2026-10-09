package org.dromara.agentoa.attendance.domain.enums;

/**
 * 工作日历日期类型（字典 at_day_type）。
 */
public enum DayType {

    WORKDAY("0"),
    HOLIDAY("1"),
    MAKEUP("2");

    private final String code;

    DayType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
