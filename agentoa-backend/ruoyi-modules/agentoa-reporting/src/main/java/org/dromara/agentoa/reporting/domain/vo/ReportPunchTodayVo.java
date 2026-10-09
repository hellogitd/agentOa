package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 今日打卡状态（与考勤自助接口同一口径）。 */
@Data
public class ReportPunchTodayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Boolean todayPunched;

    private String punchInTime;

    private String punchOutTime;
}
