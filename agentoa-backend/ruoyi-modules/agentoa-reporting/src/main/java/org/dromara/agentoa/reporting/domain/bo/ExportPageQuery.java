package org.dromara.agentoa.reporting.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 导出记录分页查询（导出接口分页与上限见 docs/18）。 */
@Data
public class ExportPageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer pageNum;

    private Integer pageSize;

    private String reportType;

    private String status;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }
}
