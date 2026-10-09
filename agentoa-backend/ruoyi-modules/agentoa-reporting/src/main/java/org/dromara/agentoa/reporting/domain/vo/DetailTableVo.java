package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 钻取明细（同一口径的分页明细行，导出列与之一致）。 */
@Data
public class DetailTableVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<HeaderVo> headers;

    private List<Map<String, Object>> rows;

    private long total;
}
