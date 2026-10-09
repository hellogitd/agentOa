package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 关键余额（假期额度可用分钟）。 */
@Data
public class ReportBalanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String leaveType;

    private Integer totalMinutes;

    private Integer frozenMinutes;

    private Integer usedMinutes;

    private Integer availableMinutes;
}
