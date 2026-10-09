package org.dromara.agentoa.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 待付款报销单视图（财务/出纳视角）。
 */
@Data
public class PendingClaimVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String reimburseNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String applicantName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    private String deptName;

    /** 两位小数金额字符串 */
    private String totalAmount;

    /** 已付金额 */
    private String paidAmount;

    private Integer status;

    private String createTime;

    private String updateTime;
}
