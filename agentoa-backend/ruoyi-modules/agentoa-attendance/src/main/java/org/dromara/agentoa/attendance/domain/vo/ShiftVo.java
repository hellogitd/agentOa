package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaShift;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * 班次视图（API 规范 5.2）。
 */
@Data
@AutoMapper(target = OaShift.class)
public class ShiftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String shiftCode;

    private String shiftName;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    private LocalTime restStartTime;

    private LocalTime restEndTime;

    private Integer isCrossDay;

    private Integer flexibleMinutes;

    private Integer graceMinutes;

    private Integer punchWindowStart;

    private Integer punchWindowEnd;

    private String status;

    private String remark;
}
