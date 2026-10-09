package org.dromara.agentoa.attendance.domain.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 额度批次视图（AT-06）。
 */
@Data
public class BatchVo {

    private Long id;

    private String batchNo;

    private Integer year;

    private String leaveType;

    private Integer grantMinutes;

    private Integer frozenMinutes;

    private Integer usedMinutes;

    private Integer expiredMinutes;

    /** 剩余可用 = grant - frozen - used - expired */
    private Integer availableMinutes;

    private LocalDate validFrom;

    private LocalDate expireDate;

    /** 状态（1有效 2已过期 3已用尽） */
    private Integer status;
}
