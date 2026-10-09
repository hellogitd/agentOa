package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 发票占用流水（docs/14 第 3 步）：OCCUPY/RELEASE/PAID 不可变记录，event_key 幂等去重。
 */
@Data
@TableName("oa_invoice_reservation")
public class OaInvoiceReservation {

    @TableId(value = "id")
    private Long id;

    private Long invoiceId;

    private Long reimburseId;

    /** 动作（OCCUPY/RELEASE/PAID） */
    private String action;

    /** 业务事件 ID（幂等去重） */
    private String eventKey;

    private Long operatorId;

    private Date createTime;
}
