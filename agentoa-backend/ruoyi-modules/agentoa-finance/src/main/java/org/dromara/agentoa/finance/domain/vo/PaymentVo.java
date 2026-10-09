package org.dromara.agentoa.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 付款登记视图。
 */
@Data
public class PaymentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String paymentNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reimburseId;

    private String reimburseNo;

    private String applicantName;

    /** 两位小数金额字符串 */
    private String amount;

    private String payDate;

    private String paymentMethod;

    private String voucherNo;

    private Integer payStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String createTime;

    private String remark;
}
