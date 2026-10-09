package org.dromara.agentoa.workflow.domain.chain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 受控条件（结构化，禁表达式注入）：仅支持金额与白名单比较算子。
 * 编译产物是白名单表达式，例如 {@code ${amount_gt_50000}} / {@code ${!amount_gt_50000}}。
 */
@Data
public class FlowCondition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 受控字段，当前仅支持 amount（报销金额等） */
    private String field;

    /** 受控算子 gt/ge/lt/le/eq/ne */
    private String op;

    /** 比较阈值（数字字面量，禁止其它形态） */
    private Number value;

    public boolean isDefaultElse() {
        return field == null || field.isBlank();
    }
}
