package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 发票分摊（docs/04 FN-05）：拆票/跨单分摊/部分付款的分配记录，event_key 幂等去重。
 */
@Data
@TableName("oa_invoice_allocation")
public class OaInvoiceAllocation {

    @TableId(value = "id")
    private Long id;

    private Long invoiceId;

    private Long reimburseId;

    /** 费用明细 ID（可空=整单分摊） */
    private Long expenseItemId;

    /** 分摊金额 */
    private BigDecimal amount;

    /** 业务事件 ID（幂等去重） */
    private String eventKey;

    private Long operatorId;

    private Date createTime;
}
