package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 考勤组视图（API 规范 5.2）。
 */
@Data
@AutoMapper(target = OaAttendanceGroup.class)
public class AttendanceGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String groupCode;

    private String groupName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long shiftId;

    private String shiftName;

    private String workDays;

    private LocalDate effectiveDate;

    private String status;

    private Long memberCount;

    private String remark;
}
