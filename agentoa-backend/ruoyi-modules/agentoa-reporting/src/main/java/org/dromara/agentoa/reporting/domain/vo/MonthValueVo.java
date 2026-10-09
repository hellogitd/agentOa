package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 趋势原始行（按月聚合）。 */
@Data
public class MonthValueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer y;

    private Integer m;

    private BigDecimal value;

    private BigDecimal value2;
}
