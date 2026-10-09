package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 批次消耗分配 oa_leave_batch_allocation（AT-06）：冻结/结算/释放/过期在批次上的分摊。
 */
@Data
@TableName("oa_leave_batch_allocation")
public class OaLeaveBatchAllocation {

    public static final String ACTION_FREEZE = "FREEZE";
    public static final String ACTION_SETTLE = "SETTLE";
    public static final String ACTION_RELEASE = "RELEASE";
    public static final String ACTION_EXPIRE = "EXPIRE";

    @TableId(value = "id")
    private Long id;

    private Long batchId;

    /** 关联 oa_leave_ledger.event_key */
    private String ledgerEventKey;

    private Integer minutes;

    /** 动作（FREEZE/SETTLE/RELEASE/EXPIRE） */
    private String action;

    private Date createTime;
}
