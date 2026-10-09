package org.dromara.agentoa.finance.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 费用统计行（部门 × 费用类型），可与报销明细对账。
 */
@Data
@ExcelIgnoreUnannotated
public class ExpenseReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "部门ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    @ExcelProperty(value = "部门")
    private String deptName;

    @ExcelProperty(value = "费用类型")
    private String expenseTypeName;

    @ExcelProperty(value = "明细笔数")
    private Integer itemCount;

    @ExcelProperty(value = "金额合计")
    private String totalAmount;
}
