package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 趋势点（日期 + 主值 + 可选次值，如入/离职双折线）。 */
@Data
public class TrendPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String date;

    private BigDecimal value;

    private BigDecimal value2;
}
