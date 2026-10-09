package org.dromara.agentoa.reporting.mapper;

import org.dromara.agentoa.reporting.domain.OaReportMetricVersion;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/** 指标口径版本 Mapper（只追加，口径变更即新增版本）。 */
public interface OaReportMetricVersionMapper extends BaseMapperPlus<OaReportMetricVersion, OaReportMetricVersion> {
}
