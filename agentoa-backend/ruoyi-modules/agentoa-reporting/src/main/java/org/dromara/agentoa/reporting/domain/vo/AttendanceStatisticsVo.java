package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 考勤看板（docs/02 9.5）。 */
@Data
public class AttendanceStatisticsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StatCardVo> cards;

    /** 近 30 天出勤率趋势（分母为零的日期不产生点）。 */
    private List<TrendPointVo> trend30;

    private List<LabelValueVo> deptLateTop;

    private List<LabelValueVo> leaveTypeDistribution;

    private List<LabelValueVo> overtimeTop;

    private List<ExceptionItemVo> exceptions;

    private List<MetricVo> metrics;
}
