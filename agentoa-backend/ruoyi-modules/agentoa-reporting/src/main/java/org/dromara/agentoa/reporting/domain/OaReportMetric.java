package org.dromara.agentoa.reporting.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 报表指标字典（docs/18 步骤 1，表 oa_report_metric）。 */
@Data
@TableName("oa_report_metric")
public class OaReportMetric implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String tenantId;

    private String metricCode;

    private String metricName;

    private String category;

    private String unit;

    private String description;

    private Integer currentVersion;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
