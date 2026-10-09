package org.dromara.agentoa.reporting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 指标字典与当前口径版本（报表版本兼容对账用）。 */
@Data
public class MetricVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String metricCode;

    private String metricName;

    private String category;

    private String unit;

    private String description;

    private Integer currentVersion;

    /** 当前口径定义 JSON。 */
    private String definition;

    private String status;
}
