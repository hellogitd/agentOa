package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 不可变额度流水 oa_leave_ledger：event_key 唯一，重复消费不重复扣减。
 */
@Data
@TableName("oa_leave_ledger")
public class OaLeaveLedger {

    @TableId(value = "id")
    private Long id;

    private Long balanceId;

    /** 业务事件 ID（幂等去重） */
    private String eventKey;

    /** grant / leave */
    private String businessType;

    private Long businessId;

    private Integer submissionNo;

    /** GRANT/FREEZE/SETTLE/RELEASE/ADJUST */
    private String action;

    private Integer totalDelta;

    private Integer frozenDelta;

    private Integer usedDelta;

    private Integer leaveMinutes;

    private Long operatorId;

    private Date createTime;
}
