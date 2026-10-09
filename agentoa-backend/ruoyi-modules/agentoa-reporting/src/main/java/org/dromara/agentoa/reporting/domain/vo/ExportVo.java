package org.dromara.agentoa.reporting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 导出任务记录（操作者、过滤条件、口径版本与文件）。 */
@Data
public class ExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String exportNo;

    private String reportType;

    private Integer metricVersion;

    private String format;

    private String filters;

    private String status;

    private Integer rowCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String errorMessage;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestedBy;

    private String createTime;

    private String finishTime;
}
