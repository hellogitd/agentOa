package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 明细表头（key 与导出列一致，label 为中文列名）。 */
@Data
public class HeaderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String key;

    private String label;

    public static HeaderVo of(String key, String label) {
        HeaderVo vo = new HeaderVo();
        vo.setKey(key);
        vo.setLabel(label);
        return vo;
    }
}
