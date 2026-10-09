package org.dromara.agentoa.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 发票视图：金额以两位小数字符串返回；明文/图片下载由 FinanceAccessPolicy 控制。
 */
@Data
public class InvoiceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String invoiceType;

    private String invoiceCode;

    private String invoiceNo;

    private LocalDate invoiceDate;

    /** 两位小数金额字符串 */
    private String amount;

    private String fingerprint;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String fileName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;

    private String ownerName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long occupiedReimburseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long paidReimburseId;

    /** 占用状态：FREE/OCCUPIED/PAID */
    private String occupationStatus;

    private String createTime;
}
