package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 考勤日报 oa_attendance_day：保存班次/考勤组快照，规则修改不重算历史日报。
 */
@Data
@TableName("oa_attendance_day")
public class OaAttendanceDay {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private Long deptId;

    private LocalDate attendanceDate;

    private Long groupId;

    private Long shiftId;

    private Integer ruleVersion;

    private LocalDateTime firstPunchTime;

    private LocalDateTime lastPunchTime;

    /** 0待结算 1出勤 2休息 3缺勤 4请假 */
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

    private Date createTime;

    private Date updateTime;
}
