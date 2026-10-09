package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 付款登记（docs/04 6.5）：一次全额人工付款，uk_payment_reimburse 保证报销单只付一次。
 */
@Data
@TableName("oa_payment_record")
public class OaPaymentRecord {

    @TableId(value = "id")
    private Long id;

    private String paymentNo;

    private Long reimburseId;

    private BigDecimal amount;

    private LocalDate payDate;

    /** 付款方式（BANK_TRANSFER/CASH/OTHER） */
    private String paymentMethod;

    private String voucherNo;

    /** 状态（1待付 2已付 3失败） */
    private Integer payStatus;

    private Long operatorId;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String remark;
}
