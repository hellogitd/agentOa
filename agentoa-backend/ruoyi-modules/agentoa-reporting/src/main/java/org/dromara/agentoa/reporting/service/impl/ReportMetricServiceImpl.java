package org.dromara.agentoa.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.reporting.domain.OaReportMetric;
import org.dromara.agentoa.reporting.domain.OaReportMetricVersion;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.vo.MetricVo;
import org.dromara.agentoa.reporting.mapper.OaReportMetricMapper;
import org.dromara.agentoa.reporting.mapper.OaReportMetricVersionMapper;
import org.dromara.agentoa.reporting.service.IReportMetricService;
import org.dromara.agentoa.reporting.service.support.MetricCodes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 指标字典实现：字典行与口径版本一起返回，口径变更即在 oa_report_metric_version 追加新版本。
 */
@Service
@RequiredArgsConstructor
public class ReportMetricServiceImpl implements IReportMetricService {

    private final OaReportMetricMapper metricMapper;
    private final OaReportMetricVersionMapper versionMapper;

    @Override
    public List<MetricVo> metrics(String category) {
        LambdaQueryWrapper<OaReportMetric> query = new LambdaQueryWrapper<OaReportMetric>()
            .eq(OaReportMetric::getStatus, 1)
            .eq(category != null && !category.isEmpty(), OaReportMetric::getCategory, category)
            .orderByAsc(OaReportMetric::getCategory)
            .orderByAsc(OaReportMetric::getMetricCode);
        List<OaReportMetric> metrics = metricMapper.selectList(query);
        if (metrics.isEmpty()) {
            return List.of();
        }
        List<String> codes = metrics.stream().map(OaReportMetric::getMetricCode).toList();
        Map<String, OaReportMetricVersion> current = versionMapper.selectList(
                new LambdaQueryWrapper<OaReportMetricVersion>()
                    .in(OaReportMetricVersion::getMetricCode, codes)
                    .orderByDesc(OaReportMetricVersion::getVersion))
            .stream()
            .collect(Collectors.toMap(OaReportMetricVersion::getMetricCode, Function.identity(),
                (left, right) -> left.getVersion() >= right.getVersion() ? left : right));
        List<MetricVo> result = new ArrayList<>();
        for (OaReportMetric metric : metrics) {
            OaReportMetricVersion version = current.get(metric.getMetricCode());
            result.add(toVo(metric, version));
        }
        return result;
    }

    @Override
    public int currentVersion(ReportType type) {
        List<String> codes = MetricCodes.METRICS_BY_TYPE.getOrDefault(type, List.of());
        if (codes.isEmpty()) {
            return 1;
        }
        return metrics(null).stream()
            .filter(metric -> codes.contains(metric.getMetricCode()))
            .map(MetricVo::getCurrentVersion)
            .filter(version -> version != null)
            .max(Comparator.naturalOrder())
            .orElse(1);
    }

    private MetricVo toVo(OaReportMetric metric, OaReportMetricVersion version) {
        MetricVo vo = new MetricVo();
        vo.setId(metric.getId());
        vo.setMetricCode(metric.getMetricCode());
        vo.setMetricName(metric.getMetricName());
        vo.setCategory(metric.getCategory());
        vo.setUnit(metric.getUnit());
        vo.setDescription(metric.getDescription());
        vo.setCurrentVersion(version != null ? version.getVersion() : metric.getCurrentVersion());
        vo.setDefinition(version != null ? version.getDefinition() : null);
        vo.setStatus(metric.getStatus() != null && metric.getStatus() == 1 ? "ACTIVE" : "DISABLED");
        return vo;
    }
}
