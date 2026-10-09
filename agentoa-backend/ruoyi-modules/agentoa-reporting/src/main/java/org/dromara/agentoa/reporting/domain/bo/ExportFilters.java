package org.dromara.agentoa.reporting.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 导出过滤条件（随导出任务记录保存，重放与对账用）。 */
@Data
public class ExportFilters implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String startDate;

    private String endDate;

    private Long deptId;

    /** ALL=数据范围内全量，DEPT=限定部门。 */
    private String scope;
}
