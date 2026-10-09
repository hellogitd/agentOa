package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 财务事件流水（docs/14 V7）：占用/释放/审批/付款，event_key 幂等去重。
 */
@Data
@TableName("oa_finance_event")
public class OaFinanceEvent {

    @TableId(value = "id")
    private Long id;

    /** 业务事件 ID（幂等去重） */
    private String eventKey;

    /** 业务类型（reimburse/invoice/payment） */
    private String bizType;

    private Long bizId;

    /** 动作（OCCUPY/RELEASE/APPROVE/PAY） */
    private String action;

    private BigDecimal amount;

    private Long operatorId;

    private String remark;

    private Date createTime;
}
