package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 费用类型（docs/04 6.1，docs/14 V7）：编码唯一，删除前检查报销明细引用。
 */
@Data
@TableName("oa_expense_type")
public class OaExpenseType {

    @TableId(value = "id")
    private Long id;

    private Long parentId;

    private String name;

    /** 类型编码（唯一） */
    private String code;

    private Integer sort;

    private Integer budgetControl;

    /** 状态（0正常 1停用） */
    private String status;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String remark;
}
