package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 分布/排名点（标签 + 数值）。 */
@Data
public class LabelValueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String label;

    private BigDecimal value;

    private BigDecimal value2;

    public static LabelValueVo of(String label, BigDecimal value) {
        LabelValueVo vo = new LabelValueVo();
        vo.setLabel(label);
        vo.setValue(value);
        return vo;
    }
}
