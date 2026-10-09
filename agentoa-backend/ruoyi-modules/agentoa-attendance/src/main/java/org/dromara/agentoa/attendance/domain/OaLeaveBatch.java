package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 假期额度批次 oa_leave_batch（AT-06）：滚动过期，FIFO 先过期先扣，冻结过期保护至结算。
 */
@Data
@TableName("oa_leave_batch")
public class OaLeaveBatch {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_EXPIRED = 2;
    public static final int STATUS_EXHAUSTED = 3;

    @TableId(value = "id")
    private Long id;

    private Long userId;

    /** 归属额度年度（与 oa_leave_balance 对齐） */
    private Integer year;

    private String leaveType;

    private String batchNo;

    private Integer grantMinutes;

    private Integer frozenMinutes;

    private Integer usedMinutes;

    private Integer expiredMinutes;

    private LocalDate validFrom;

    /** 当日有效，次日起过期 */
    private LocalDate expireDate;

    /** 创建事件（幂等去重） */
    private String eventKey;

    /** 状态（1有效 2已过期 3已用尽） */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
