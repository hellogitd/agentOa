package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录视图（API 规范 5.1）。
 */
@Data
@AutoMapper(target = OaPunchRecord.class)
public class PunchRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private LocalDate punchDate;

    private LocalDateTime punchTime;

    private Integer punchType;

    private Integer isLate;

    private Integer lateMinutes;

    private Integer isEarly;

    private Integer earlyMinutes;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private BigDecimal accuracyMeters;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long photoFileId;

    private String device;

    private String ip;

    private Integer source;
}
