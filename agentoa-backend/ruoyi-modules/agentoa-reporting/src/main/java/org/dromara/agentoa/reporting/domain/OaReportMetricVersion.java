package org.dromara.agentoa.reporting.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 报表口径版本（docs/18 步骤 2，口径变更即升版本，表 oa_report_metric_version）。 */
@Data
@TableName("oa_report_metric_version")
public class OaReportMetricVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String metricCode;

    private Integer version;

    /** 口径定义 JSON：时间范围、时区、状态、去重、组织过滤、数据更新时间。 */
    private String definition;

    /** 1 生效 2 历史。 */
    private Integer status;

    private LocalDateTime effectiveTime;

    private Long createdBy;

    private LocalDateTime createTime;
}
