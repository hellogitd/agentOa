package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 人事看板（docs/02 9.4）。 */
@Data
public class HrStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StatCardVo> cards;

    private List<LabelValueVo> deptDistribution;

    private List<LabelValueVo> levelDistribution;

    private List<LabelValueVo> tenureDistribution;

    /** 近 12 个月入离职趋势：value 入职、value2 离职。 */
    private List<TrendPointVo> trend12m;

    /** 口径版本（导出与对账用）。 */
    private List<MetricVo> metrics;
}
