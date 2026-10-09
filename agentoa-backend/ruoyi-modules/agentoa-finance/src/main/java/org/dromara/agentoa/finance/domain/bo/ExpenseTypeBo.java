package org.dromara.agentoa.finance.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 费用类型维护入参。
 */
@Data
public class ExpenseTypeBo {

    private Long id;

    private Long parentId;

    @NotBlank(message = "类型名称不能为空")
    @Size(max = 64, message = "类型名称长度不能超过{max}个字符")
    private String name;

    @NotBlank(message = "类型编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,31}$", message = "类型编码取值非法")
    private String code;

    private Integer sort;

    private Integer budgetControl;

    @Pattern(regexp = "0|1", message = "状态取值非法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
