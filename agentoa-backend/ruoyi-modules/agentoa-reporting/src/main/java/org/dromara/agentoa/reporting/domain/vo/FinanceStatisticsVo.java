package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 财务看板（docs/02 9.6；申请/批准/实付分列，预算执行 P1 未启用）。 */
@Data
public class FinanceStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StatCardVo> cards;

    /** 近 12 个月：value 申请额、value2 批准额。 */
    private List<TrendPointVo> trend12m;

    private List<LabelValueVo> typeDistribution;

    private List<LabelValueVo> deptTop;

    private List<MetricVo> metrics;
}
