package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaHoliday;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 节假日视图。
 */
@Data
@AutoMapper(target = OaHoliday.class)
public class HolidayVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private LocalDate holidayDate;

    private String holidayName;

    private String holidayType;

    private Integer year;
}
