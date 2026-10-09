package org.dromara.agentoa.attendance.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 考勤月报（API 规范 5.5）：可与日报逐日对账，导出 Excel 供薪资核算。
 */
@Data
@ExcelIgnoreUnannotated
public class MonthlyReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "月份")
    private String yearMonth;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @ExcelProperty(value = "姓名")
    private String nickname;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    @ExcelProperty(value = "出勤天数")
    private Integer attendanceDays;

    @ExcelProperty(value = "迟到次数")
    private Integer lateCount;

    @ExcelProperty(value = "早退次数")
    private Integer earlyCount;

    @ExcelProperty(value = "旷工次数")
    private Integer absentCount;

    @ExcelProperty(value = "请假分钟")
    private Integer leaveMinutes;

    @ExcelProperty(value = "加班分钟")
    private Integer overtimeMinutes;

    @ExcelProperty(value = "异常天数")
    private Integer abnormalCount;
}
