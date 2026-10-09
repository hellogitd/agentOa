package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日报视图（API 规范 5.5 / docs/13）。
 */
@Data
@AutoMapper(target = OaAttendanceDay.class)
public class AttendanceDayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    private LocalDate attendanceDate;

    private LocalDateTime firstPunchTime;

    private LocalDateTime lastPunchTime;

    private Integer workStatus;

    private Integer missingPunch;

    private Integer scheduledMinutes;

    private Integer workedMinutes;

    private Integer lateMinutes;

    private Integer earlyMinutes;

    private Integer leaveMinutes;

    private Integer overtimeMinutes;

    private Integer isAbnormal;

    private String abnormalReason;
}
