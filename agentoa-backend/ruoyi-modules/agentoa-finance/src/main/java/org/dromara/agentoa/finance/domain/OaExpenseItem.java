package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 报销明细（docs/04 6.3）：提交时按承接单 details_json 固化，是财务口径的唯一总额来源。
 */
@Data
@TableName("oa_expense_item")
public class OaExpenseItem {

    @TableId(value = "id")
    private Long id;

    /** 报销单 ID（oa_reimburse_request） */
    private Long reimburseId;

    private Integer submissionNo;

    private Long invoiceId;

    private Long expenseTypeId;

    /** 费用类型编码快照 */
    private String expenseType;

    private LocalDate occurDate;

    private BigDecimal amount;

    private String description;

    private Integer sort;

    private Date createTime;
}
