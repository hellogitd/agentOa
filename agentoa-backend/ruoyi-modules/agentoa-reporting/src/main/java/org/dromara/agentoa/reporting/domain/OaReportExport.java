package org.dromara.agentoa.reporting.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 报表导出任务记录（docs/18 步骤 4，记录操作者、过滤条件与口径版本）。 */
@Data
@TableName("oa_report_export")
public class OaReportExport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;

    private String exportNo;

    private String reportType;

    private Integer metricVersion;

    private String format;

    private String filters;

    /** PENDING/RUNNING/SUCCESS/FAILED。 */
    private String status;

    private Integer rowCount;

    private Long fileId;

    private String errorMessage;

    private String idempotencyKey;

    private Long requestedBy;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
