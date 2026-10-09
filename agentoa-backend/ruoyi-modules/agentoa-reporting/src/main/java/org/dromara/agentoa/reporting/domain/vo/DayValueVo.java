package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 趋势原始行（按日聚合）。 */
@Data
public class DayValueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate d;

    private BigDecimal value;

    private BigDecimal value2;
}
