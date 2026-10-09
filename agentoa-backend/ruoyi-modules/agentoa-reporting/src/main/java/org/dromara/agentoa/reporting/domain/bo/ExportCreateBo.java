package org.dromara.agentoa.reporting.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 导出任务创建（docs/18 步骤 4：必须携带 Idempotency-Key，有上限与口径版本快照）。 */
@Data
public class ExportCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报表类型：hr/attendance/finance/flow。 */
    @NotBlank(message = "报表类型不能为空")
    private String reportType;

    /** 导出格式：CSV/XLSX，默认 CSV。 */
    private String format;

    private String startDate;

    private String endDate;

    private Long deptId;
}
