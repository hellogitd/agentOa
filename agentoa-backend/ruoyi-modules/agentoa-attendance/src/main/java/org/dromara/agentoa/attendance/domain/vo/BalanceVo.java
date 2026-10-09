package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 假期余额视图（API 规范 5.3）：不计额度假种 availableMinutes 为 null。
 */
@Data
public class BalanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer year;

    private String leaveType;

    private String typeName;

    private Boolean quotaLimited;

    private Integer totalMinutes;

    private Integer frozenMinutes;

    private Integer usedMinutes;

    private Integer availableMinutes;

    private LocalDate expireDate;
}
