package org.dromara.agentoa.reporting.service;

import org.dromara.agentoa.reporting.domain.bo.DetailQueryBo;
import org.dromara.agentoa.reporting.domain.bo.ReportRangeBo;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.vo.AttendanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.DetailTableVo;
import org.dromara.agentoa.reporting.domain.vo.FinanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.FlowStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.HrStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.WorkbenchVo;
import org.dromara.agentoa.reporting.service.support.ReportRange;
import org.dromara.agentoa.reporting.service.support.ReportTable;

/**
 * 报表只读查询（docs/18 步骤 3）：看板、钻取明细与导出共用同一口径与同一数据范围。
 */
public interface IReportQueryService {

    WorkbenchVo workbench();

    HrStatisticsVo hr(ReportRangeBo query);

    AttendanceStatisticsVo attendance(ReportRangeBo query);

    FinanceStatisticsVo finance(ReportRangeBo query);

    FlowStatisticsVo flow(ReportRangeBo query);

    /** 钻取明细（分页，导出与列表同一过滤口径）。 */
    DetailTableVo details(String type, DetailQueryBo query);

    /** 导出用明细表（行上限由调用方控制）。 */
    ReportTable table(ReportType type, ReportRange range, Long deptId, int cap);

    /** 解析数据范围：全量角色返回请求部门（可空），否则强制本部门。 */
    Long resolveDeptId(Long requestedDeptId);
}
