package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaCalendar;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 工作日历视图。
 */
@Data
@AutoMapper(target = OaCalendar.class)
public class CalendarVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private LocalDate workDate;

    private String dayType;

    private String description;

    private Integer ruleVersion;
}
