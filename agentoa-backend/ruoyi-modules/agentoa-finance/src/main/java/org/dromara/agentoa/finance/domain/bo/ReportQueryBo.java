package org.dromara.agentoa.finance.domain.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 费用统计查询（docs/14 第 6 步）：按部门/费用类型汇总，可选日期区间。
 */
@Data
public class ReportQueryBo {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Long deptId;

    private String expenseType;
}
