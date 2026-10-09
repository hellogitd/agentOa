package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 排班指派命令（P1，需求 AT-03）。
 */
@Data
public class ScheduleAssignmentBo {

    @NotNull(message = "人员不能为空")
    private Long userId;

    @NotNull(message = "班次不能为空")
    private Long shiftId;

    /** 单日排班 */
    private LocalDate workDate;

    /** 区间排班（含首尾），与 workDate 二选一 */
    private LocalDate dateFrom;

    private LocalDate dateTo;

    /** 来源（1手工 2轮班） */
    private Integer source = 1;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
