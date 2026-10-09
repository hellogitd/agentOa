package org.dromara.agentoa.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算视图（API 规范 6.3）。
 */
@Data
public class BudgetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String budgetCode;

    private String budgetName;

    /** 类型（1部门 2项目） */
    private Integer budgetType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private Integer year;

    private Integer quarter;

    private BigDecimal totalAmount;

    private BigDecimal usedAmount;

    private BigDecimal frozenAmount;

    /** 可用额度 = 总额 - 已用 - 冻结 */
    private BigDecimal availableAmount;

    /** 预警阈值（%） */
    private Integer warnThreshold;

    /** 是否超预警阈值 */
    private Boolean warn;

    /** 状态（1执行 2关闭） */
    private Integer status;

    private String remark;
}
