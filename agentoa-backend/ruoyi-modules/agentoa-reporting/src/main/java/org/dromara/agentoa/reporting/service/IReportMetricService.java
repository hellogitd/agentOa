package org.dromara.agentoa.reporting.service;

import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.vo.MetricVo;

import java.util.List;

/** 指标字典与口径版本（docs/18 步骤 2）。 */
public interface IReportMetricService {

    /** 指标字典（含当前口径定义）；category 可空表示全部。 */
    List<MetricVo> metrics(String category);

    /** 报表类型的口径版本快照（集合内最大当前版本）。 */
    int currentVersion(ReportType type);
}
