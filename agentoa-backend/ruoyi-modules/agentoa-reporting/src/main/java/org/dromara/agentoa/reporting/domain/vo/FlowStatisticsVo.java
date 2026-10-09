package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 流程效率看板（docs/02 9.3；拒绝/撤销/终止单列不进平均时长）。 */
@Data
public class FlowStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StatCardVo> cards;

    /** 近 30 天每日发起流程数。 */
    private List<TrendPointVo> trend30;

    private List<LabelValueVo> byType;

    private List<LabelValueVo> topSlow;

    private List<ExceptionItemVo> timeouts;

    private List<MetricVo> metrics;
}
