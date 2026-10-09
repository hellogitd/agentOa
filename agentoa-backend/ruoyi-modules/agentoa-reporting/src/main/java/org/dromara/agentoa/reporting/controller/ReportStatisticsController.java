package org.dromara.agentoa.reporting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.reporting.domain.bo.DetailQueryBo;
import org.dromara.agentoa.reporting.domain.bo.ReportRangeBo;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.domain.vo.AttendanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.DetailTableVo;
import org.dromara.agentoa.reporting.domain.vo.FinanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.FlowStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.HrStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.MetricVo;
import org.dromara.agentoa.reporting.service.IReportMetricService;
import org.dromara.agentoa.reporting.service.IReportQueryService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 四类看板接口（API 规范 docs/05 第 10 节）：人事、考勤、财务、流程。
 * 所有查询先应用对象权限与部门范围（docs/18 步骤 3），报表为只读，不修改业务事实。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/report")
public class ReportStatisticsController {

    private final IReportQueryService queryService;
    private final IReportMetricService metricService;

    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/hr/statistics")
    public R<HrStatisticsVo> hr(ReportRangeBo query) {
        return R.ok(queryService.hr(query));
    }

    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/attendance/statistics")
    public R<AttendanceStatisticsVo> attendance(ReportRangeBo query) {
        return R.ok(queryService.attendance(query));
    }

    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/finance/statistics")
    public R<FinanceStatisticsVo> finance(ReportRangeBo query) {
        return R.ok(queryService.finance(query));
    }

    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/flow/statistics")
    public R<FlowStatisticsVo> flow(ReportRangeBo query) {
        return R.ok(queryService.flow(query));
    }

    /** 指标字典与当前口径版本（报表版本兼容对账）。 */
    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/metrics")
    public R<List<MetricVo>> metrics(@RequestParam(required = false) String category) {
        return R.ok(metricService.metrics(category));
    }

    /** 钻取明细：与看板/导出同一口径，分页取行。 */
    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_LIST)
    @GetMapping("/details/{type}")
    public R<DetailTableVo> details(@PathVariable String type, DetailQueryBo query) {
        if (query == null) {
            throw new ServiceException("RP_QUERY_INVALID 查询条件不能为空", 400);
        }
        return R.ok(queryService.details(type, query));
    }
}
