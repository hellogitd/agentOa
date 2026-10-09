package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 指标卡片（名称、单位与格式化取值）。 */
@Data
public class StatCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;

    private String name;

    private String unit;

    /** 展示值（空值表示分母为零/无数据，见 docs/02 13.4）。 */
    private String value;

    private BigDecimal raw;

    public static StatCardVo of(String code, String name, String unit, String value, BigDecimal raw) {
        StatCardVo card = new StatCardVo();
        card.setCode(code);
        card.setName(name);
        card.setUnit(unit);
        card.setValue(value);
        card.setRaw(raw);
        return card;
    }
}
