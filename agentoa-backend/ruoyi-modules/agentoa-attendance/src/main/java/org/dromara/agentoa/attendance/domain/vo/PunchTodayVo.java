package org.dromara.agentoa.attendance.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 今日打卡状态（工作台/今日打卡页使用）。
 */
@Data
public class PunchTodayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String attendanceDate;

    private Boolean todayPunched;

    private LocalDateTime punchInTime;

    private LocalDateTime punchOutTime;

    private Integer workStatus;

    private Integer scheduledMinutes;

    private Integer workedMinutes;

    private Integer lateMinutes;

    private Integer earlyMinutes;

    private Integer leaveMinutes;

    private Integer isAbnormal;

    private String abnormalReason;
}
