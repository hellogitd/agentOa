package org.dromara.agentoa.finance.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 预算业务对象（API 规范 6.3）。既做查询条件也做新增/修改入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudgetBo extends BaseEntity {

    private Long id;

    @Size(max = 32, message = "预算编号长度不能超过{max}个字符")
    private String budgetCode;

    @Size(max = 64, message = "预算名称长度不能超过{max}个字符")
    private String budgetName;

    /** 类型（1部门 2项目） */
    @NotNull(message = "预算类型不能为空")
    @Min(value = 1, message = "预算类型取值非法")
    @Max(value = 2, message = "预算类型取值非法")
    private Integer budgetType;

    @NotNull(message = "归属不能为空")
    private Long ownerId;

    @NotNull(message = "年度不能为空")
    private Integer year;

    /** 季度（1-4，空=年度） */
    @Min(value = 1, message = "季度取值非法")
    @Max(value = 4, message = "季度取值非法")
    private Integer quarter;

    @NotNull(message = "预算总额不能为空")
    @DecimalMin(value = "0.00", message = "预算总额不能为负数")
    private BigDecimal totalAmount;

    /** 预警阈值（%） */
    @Min(value = 1, message = "预警阈值取值非法")
    @Max(value = 100, message = "预警阈值取值非法")
    private Integer warnThreshold;

    /** 状态（1执行 2关闭） */
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
