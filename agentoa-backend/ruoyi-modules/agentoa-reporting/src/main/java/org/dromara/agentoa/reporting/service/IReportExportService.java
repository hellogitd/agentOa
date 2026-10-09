package org.dromara.agentoa.reporting.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.reporting.domain.bo.ExportCreateBo;
import org.dromara.agentoa.reporting.domain.bo.ExportFilters;
import org.dromara.agentoa.reporting.domain.bo.ExportPageQuery;
import org.dromara.agentoa.reporting.domain.enums.ExportFormat;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.vo.ExportVo;
import org.dromara.agentoa.reporting.service.support.ReportRange;
import org.dromara.agentoa.reporting.service.support.ReportTable;

/** 报表导出（docs/18 步骤 3/4）：同步导出与异步导出任务共用同一口径与数据范围。 */
public interface IReportExportService {

    /** 创建异步导出任务（必须携带 Idempotency-Key；同键同请求重放，同键异请求 409）。 */
    ExportVo create(ExportCreateBo bo, String idempotencyKey);

    /** 导出任务详情（操作者本人或 rp:report:export）。 */
    ExportVo get(Long id);

    /** 导出记录分页（操作者看本人，rp:report:export 看全量）。 */
    PageVo<ExportVo> list(ExportPageQuery query);

    /** 处理待办导出任务（由调度器调用；无登录上下文，按任务记录的范围执行）。 */
    void processPending();

    /** 同步导出：返回表内容并记录操作者、过滤条件与口径版本。 */
    ReportTable syncExport(ReportType type, ExportFormat format, ExportFilters filters);

    /** 过滤条件换算为查询区间（默认本月）。 */
    ReportRange rangeOf(ExportFilters filters);
}
