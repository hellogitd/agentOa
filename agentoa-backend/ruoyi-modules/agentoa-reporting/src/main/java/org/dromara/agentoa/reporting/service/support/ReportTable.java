package org.dromara.agentoa.reporting.service.support;

import java.util.List;

/** 报表导出表（表头 + 行，CSV/XLSX 共用同一口径结果）。 */
public record ReportTable(List<String> headers, List<List<String>> rows) {
}
