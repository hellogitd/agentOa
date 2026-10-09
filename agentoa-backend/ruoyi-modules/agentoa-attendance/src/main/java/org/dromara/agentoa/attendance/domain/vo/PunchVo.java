package org.dromara.agentoa.attendance.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打卡结果（API 规范 5.1 响应体）。
 */
@Data
public class PunchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime punchTime;

    private Integer punchType;

    private Boolean isLate;

    private Integer lateMinutes;

    private Boolean isEarly;

    private Integer earlyMinutes;

    private String location;
}
