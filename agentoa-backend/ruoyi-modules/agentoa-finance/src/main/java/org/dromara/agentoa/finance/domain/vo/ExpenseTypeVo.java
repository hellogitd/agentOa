package org.dromara.agentoa.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 费用类型视图（树形）。
 */
@Data
public class ExpenseTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String name;

    private String code;

    private Integer sort;

    private Integer budgetControl;

    private String status;

    private String remark;

    private List<ExpenseTypeVo> children;
}
