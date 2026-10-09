package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 发票与当前报销占用（docs/04 14.2）：身份唯一，占用/付款由条件更新控制并发。
 */
@Data
@TableName("oa_invoice")
public class OaInvoice {

    @TableId(value = "id")
    private Long id;

    private String invoiceType;

    /** 发票代码（无代码为空串） */
    private String invoiceCode;

    private String invoiceNo;

    private LocalDate invoiceDate;

    private BigDecimal amount;

    /** 规范化身份指纹（SHA-256），供审计 */
    private String fingerprint;

    private Long fileId;

    private Long ownerUserId;

    /** 在途占用报销单 ID */
    private Long occupiedReimburseId;

    /** 已付款报销单 ID（永久占用） */
    private Long paidReimburseId;

    private Integer lockVersion;

    private Date createTime;

    private Date updateTime;
}
