package org.dromara.agentoa.reporting.service.support;

import org.dromara.agentoa.reporting.domain.enums.ReportType;

import java.util.List;
import java.util.Map;

/** 指标编码常量与报表类型对应的指标集合（口径版本快照按集合内最大版本登记）。 */
public final class MetricCodes {

    public static final String HR_ACTIVE_COUNT = "hr.active_count";
    public static final String HR_NEW_HIRE_MONTH = "hr.new_hire_month";
    public static final String HR_LEAVE_MONTH = "hr.leave_month";
    public static final String HR_PROBATION_COUNT = "hr.probation_count";
    public static final String HR_DEPT_DISTRIBUTION = "hr.dept_distribution";

    public static final String ATT_RATE_TODAY = "att.rate_today";
    public static final String ATT_LATE_WEEK = "att.late_week";
    public static final String ATT_OVERTIME_MONTH = "att.overtime_month";
    public static final String ATT_LEAVE_DAYS_MONTH = "att.leave_days_month";

    public static final String FN_APPLIED_MONTH = "fn.applied_month";
    public static final String FN_APPROVED_MONTH = "fn.approved_month";
    public static final String FN_PAID_MONTH = "fn.paid_month";
    public static final String FN_PENDING_AMOUNT = "fn.pending_amount";

    public static final String FLOW_TODO_COUNT = "flow.todo_count";
    public static final String FLOW_HANDLED_WEEK = "flow.handled_week";
    public static final String FLOW_TIMEOUT_COUNT = "flow.timeout_count";
    public static final String FLOW_AVG_DURATION_HOURS = "flow.avg_duration_hours";
    public static final String FLOW_BY_TYPE = "flow.by_type";

    /** 流程超时阈值（小时），口径版本 flow.timeout_count.thresholdHours。 */
    public static final int TIMEOUT_HOURS = 24;

    public static final Map<ReportType, List<String>> METRICS_BY_TYPE = Map.of(
        ReportType.HR, List.of(HR_ACTIVE_COUNT, HR_NEW_HIRE_MONTH, HR_LEAVE_MONTH, HR_PROBATION_COUNT,
            HR_DEPT_DISTRIBUTION),
        ReportType.ATTENDANCE, List.of(ATT_RATE_TODAY, ATT_LATE_WEEK, ATT_OVERTIME_MONTH, ATT_LEAVE_DAYS_MONTH),
        ReportType.FINANCE, List.of(FN_APPLIED_MONTH, FN_APPROVED_MONTH, FN_PAID_MONTH, FN_PENDING_AMOUNT),
        ReportType.FLOW, List.of(FLOW_TODO_COUNT, FLOW_HANDLED_WEEK, FLOW_TIMEOUT_COUNT, FLOW_AVG_DURATION_HOURS,
            FLOW_BY_TYPE));

    private MetricCodes() {
    }
}
