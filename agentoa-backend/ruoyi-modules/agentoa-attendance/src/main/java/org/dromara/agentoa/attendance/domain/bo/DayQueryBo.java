package org.dromara.agentoa.attendance.domain.bo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 考勤日报/报表查询条件。
 */
@Data
public class DayQueryBo {

    /** 为空表示按权限范围 */
    private Long userId;

    private Long deptId;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    /** yyyy-MM，月报查询用 */
    private String yearMonth;

    /** 仅异常 */
    private Boolean abnormalOnly;
}
