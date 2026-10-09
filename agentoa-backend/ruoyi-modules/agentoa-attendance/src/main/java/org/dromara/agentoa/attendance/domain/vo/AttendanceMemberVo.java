package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaAttendanceMember;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 考勤组成员视图。
 */
@Data
@AutoMapper(target = OaAttendanceMember.class)
public class AttendanceMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    private LocalDate validFrom;

    private LocalDate validTo;
}
