package org.dromara.agentoa.reporting.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 钻取明细查询：在看板同一口径下分页取明细行。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DetailQueryBo extends ReportRangeBo {

    private Integer pageNum;

    private Integer pageSize;

    /** 钻取维度键（如部门ID、费用类型编码、业务类型）。 */
    private String key;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }
}
