package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaLeaveType;

import java.io.Serial;
import java.io.Serializable;

/**
 * 假期类型视图。
 */
@Data
@AutoMapper(target = OaLeaveType.class)
public class LeaveTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String typeCode;

    private String typeName;

    private Integer quotaLimited;

    private Integer defaultMinutes;

    private String status;

    private String remark;
}
