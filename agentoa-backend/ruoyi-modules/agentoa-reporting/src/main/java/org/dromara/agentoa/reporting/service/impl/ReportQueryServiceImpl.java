package org.dromara.agentoa.reporting.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.reporting.domain.bo.DetailQueryBo;
import org.dromara.agentoa.reporting.domain.bo.ReportRangeBo;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.domain.vo.AttendanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.ReportBalanceVo;
import org.dromara.agentoa.reporting.domain.vo.DayValueVo;
import org.dromara.agentoa.reporting.domain.vo.DetailTableVo;
import org.dromara.agentoa.reporting.domain.vo.ExceptionItemVo;
import org.dromara.agentoa.reporting.domain.vo.FinanceStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.FlowStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.HeaderVo;
import org.dromara.agentoa.reporting.domain.vo.HrStatisticsVo;
import org.dromara.agentoa.reporting.domain.vo.LabelValueVo;
import org.dromara.agentoa.reporting.domain.vo.MetricVo;
import org.dromara.agentoa.reporting.domain.vo.MonthValueVo;
import org.dromara.agentoa.reporting.domain.vo.ReportPunchTodayVo;
import org.dromara.agentoa.reporting.domain.vo.QuickLinkVo;
import org.dromara.agentoa.reporting.domain.vo.StatCardVo;
import org.dromara.agentoa.reporting.domain.vo.TrendPointVo;
import org.dromara.agentoa.reporting.domain.vo.WorkItemVo;
import org.dromara.agentoa.reporting.domain.vo.WorkNoticeVo;
import org.dromara.agentoa.reporting.domain.vo.WorkbenchManagementVo;
import org.dromara.agentoa.reporting.domain.vo.WorkbenchVo;
import org.dromara.agentoa.reporting.domain.vo.WorkEventVo;
import org.dromara.agentoa.reporting.mapper.ReportQueryMapper;
import org.dromara.agentoa.reporting.mapper.ReportingIdentityReadMapper;
import org.dromara.agentoa.reporting.service.IReportMetricService;
import org.dromara.agentoa.reporting.service.IReportQueryService;
import org.dromara.agentoa.reporting.service.support.MetricCodes;
import org.dromara.agentoa.reporting.service.support.ReportMasker;
import org.dromara.agentoa.reporting.service.support.ReportRange;
import org.dromara.agentoa.reporting.service.support.ReportTable;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表查询实现（docs/18）：只读口径读取、先应用对象权限与部门范围，报表不修改业务事实。
 * 列表与报表对账依赖同一 SQL 口径（见 ReportQueryMapper 注释与指标口径版本）。
 */
@Service
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements IReportQueryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TREND_DAYS = 30;
    private static final int TREND_MONTHS = 12;
    private static final int TOP_LIMIT = 5;
    private static final int EXCEPTION_LIMIT = 10;

    private final ReportQueryMapper queryMapper;
    private final ReportingIdentityReadMapper identityMapper;
    private final IReportMetricService metricService;

    // ---------------------------------------------------------------- 工作台

    @Override
    public WorkbenchVo workbench() {
        Long userId = LoginHelper.getUserId();
        LocalDate today = LocalDate.now();
        WorkbenchVo vo = new WorkbenchVo();
        vo.setTodoCount(nz(queryMapper.countMyTodo(String.valueOf(userId))));
        vo.setInitiatedCount(nz(queryMapper.countMyInitiated(userId, null)));
        vo.setTodoRecent(workItems(queryMapper.myTodoRecent(String.valueOf(userId), 5)));
        vo.setInitiatedRecent(workItems(queryMapper.myInitiatedRecent(userId, 5)));
        vo.setTodayEvents(workEvents(queryMapper.todayEvents(userId,
            today.atStartOfDay(), today.plusDays(1).atStartOfDay(), 10)));
        vo.setNotices(workNotices(queryMapper.noticeRecent(userId, 3)));

        ReportPunchTodayVo punch = new ReportPunchTodayVo();
        Map<String, Object> punches = queryMapper.punchOfDay(userId, today);
        Object inTime = punches == null ? null : punches.get("punch_in");
        Object outTime = punches == null ? null : punches.get("punch_out");
        punch.setTodayPunched(inTime != null || outTime != null);
        punch.setPunchInTime(fmtTime(inTime));
        punch.setPunchOutTime(fmtTime(outTime));
        vo.setAttendance(punch);

        List<ReportBalanceVo> balances = new ArrayList<>();
        for (ReportBalanceVo balance : queryMapper.leaveBalances(userId, today.getYear())) {
            int available = nz(balance.getTotalMinutes()) - nz(balance.getFrozenMinutes()) - nz(balance.getUsedMinutes());
            balance.setAvailableMinutes(available);
            balances.add(balance);
        }
        vo.setLeaveBalances(balances);

        vo.setQuickLinks(List.of(
            QuickLinkVo.of("发起请假", "/workflow/mine"),
            QuickLinkVo.of("发起报销", "/finance/reimburse"),
            QuickLinkVo.of("补卡申请", "/workflow/mine"),
            QuickLinkVo.of("创建文档", "/knowledge/document"),
            QuickLinkVo.of("新建任务", "/collaboration/task")));

        if (ReportingAccessPolicy.canViewWorkbenchManagement(LoginHelper.isSuperAdmin(), permissions())) {
            WorkbenchManagementVo management = new WorkbenchManagementVo();
            Long deptId = identityMapper.selectUserDeptId(userId);
            ReportRange month = ReportRange.currentMonth();
            Map<String, Object> rate = queryMapper.attendanceRate(deptId, month.start(), month.endExclusive());
            management.setDeptAttendanceRate(ReportRange.ratio(dec(rate, "value"), dec(rate, "value2")));
            management.setMonthReimburseAmount(ReportRange.amount(queryMapper.sumAppliedAmount(deptId,
                month.startDateTime(), month.endExclusiveDateTime())));
            management.setAvgFlowDurationHours(decimal(queryMapper.avgDurationHours(deptId,
                month.startDateTime(), month.endExclusiveDateTime()), 2));
            vo.setManagement(management);
        }
        return vo;
    }

    // ---------------------------------------------------------------- 人事看板

    @Override
    public HrStatisticsVo hr(ReportRangeBo query) {
        Long deptId = resolveDeptId(query == null ? null : query.getDeptId());
        ReportRange month = ReportRange.currentMonth();
        ReportRange range = rangeOf(query, month);
        HrStatisticsVo vo = new HrStatisticsVo();
        List<StatCardVo> cards = new ArrayList<>();
        long active = nz(queryMapper.countActiveEmployees(deptId));
        cards.add(StatCardVo.of(MetricCodes.HR_ACTIVE_COUNT, "在职总人数", "count",
            String.valueOf(active), BigDecimal.valueOf(active)));
        long newHires = nz(queryMapper.countNewHires(deptId, range.start(), range.endExclusive()));
        cards.add(StatCardVo.of(MetricCodes.HR_NEW_HIRE_MONTH, "本月新入职", "count",
            String.valueOf(newHires), BigDecimal.valueOf(newHires)));
        long leavers = nz(queryMapper.countLeavers(deptId, range.start(), range.endExclusive()));
        cards.add(StatCardVo.of(MetricCodes.HR_LEAVE_MONTH, "本月离职", "count",
            String.valueOf(leavers), BigDecimal.valueOf(leavers)));
        long probation = nz(queryMapper.countProbation(deptId));
        cards.add(StatCardVo.of(MetricCodes.HR_PROBATION_COUNT, "试用期人数", "count",
            String.valueOf(probation), BigDecimal.valueOf(probation)));
        vo.setCards(cards);
        vo.setDeptDistribution(queryMapper.deptDistribution(deptId));
        vo.setLevelDistribution(queryMapper.levelDistribution(deptId));
        vo.setTenureDistribution(queryMapper.tenureDistribution(deptId));
        ReportRange year = ReportRange.last12Months();
        vo.setTrend12m(fillMonths(queryMapper.hireTrend(deptId, year.start(), year.endExclusive()), year));
        vo.setMetrics(metricsFor(ReportType.HR));
        return vo;
    }

    // ---------------------------------------------------------------- 考勤看板

    @Override
    public AttendanceStatisticsVo attendance(ReportRangeBo query) {
        Long deptId = resolveDeptId(query == null ? null : query.getDeptId());
        LocalDate today = LocalDate.now();
        ReportRange todayRange = ReportRange.of(today, today);
        ReportRange week = weekRange();
        ReportRange month = ReportRange.currentMonth();
        ReportRange last30 = ReportRange.lastDays(TREND_DAYS);

        AttendanceStatisticsVo vo = new AttendanceStatisticsVo();
        List<StatCardVo> cards = new ArrayList<>();
        Map<String, Object> todayRate = queryMapper.attendanceRate(deptId, todayRange.start(), todayRange.endExclusive());
        cards.add(StatCardVo.of(MetricCodes.ATT_RATE_TODAY, "今日出勤率", "ratio",
            ReportRange.ratio(dec(todayRate, "value"), dec(todayRate, "value2")), null));
        long lateWeek = nz(queryMapper.countLateDays(deptId, week.start(), week.endExclusive()));
        cards.add(StatCardVo.of(MetricCodes.ATT_LATE_WEEK, "本周迟到次数", "count",
            String.valueOf(lateWeek), BigDecimal.valueOf(lateWeek)));
        long overtimeMonth = nz(queryMapper.sumOvertimeMinutes(deptId, month.start(), month.endExclusive()));
        cards.add(StatCardVo.of(MetricCodes.ATT_OVERTIME_MONTH, "本月加班时长", "minutes",
            String.valueOf(overtimeMonth), BigDecimal.valueOf(overtimeMonth)));
        long leaveMinutes = nz(queryMapper.sumLeaveMinutes(deptId, month.start(), month.endExclusive()));
        cards.add(StatCardVo.of(MetricCodes.ATT_LEAVE_DAYS_MONTH, "本月请假天数", "days",
            ReportRange.minutesToDays(leaveMinutes), BigDecimal.valueOf(leaveMinutes)));
        vo.setCards(cards);

        List<TrendPointVo> trend = new ArrayList<>();
        Map<LocalDate, DayValueVo> byDay = new LinkedHashMap<>();
        for (DayValueVo row : queryMapper.attendanceRateTrend(deptId, last30.start(), last30.endExclusive())) {
            byDay.put(row.getD(), row);
        }
        for (LocalDate day = last30.start(); day.isBefore(last30.endExclusive()); day = day.plusDays(1)) {
            TrendPointVo point = new TrendPointVo();
            point.setDate(day.toString());
            DayValueVo row = byDay.get(day);
            point.setValue(row == null ? null : ratioRaw(row.getValue(), row.getValue2()));
            trend.add(point);
        }
        vo.setTrend30(trend);

        ReportRange range = rangeOf(query, month);
        vo.setDeptLateTop(queryMapper.lateTopByDept(deptId, range.start(), range.endExclusive(), TOP_LIMIT));
        vo.setLeaveTypeDistribution(queryMapper.leaveTypeDistribution(deptId, range.start(), range.endExclusive()));
        vo.setOvertimeTop(queryMapper.overtimeTopByUser(deptId, range.start(), range.endExclusive(), TOP_LIMIT));
        vo.setExceptions(exceptionItems(queryMapper.attendanceExceptions(deptId, range.start(), range.endExclusive(),
            EXCEPTION_LIMIT)));
        vo.setMetrics(metricsFor(ReportType.ATTENDANCE));
        return vo;
    }

    // ---------------------------------------------------------------- 财务看板

    @Override
    public FinanceStatisticsVo finance(ReportRangeBo query) {
        Long deptId = resolveDeptId(query == null ? null : query.getDeptId());
        ReportRange month = ReportRange.currentMonth();
        ReportRange range = rangeOf(query, month);
        FinanceStatisticsVo vo = new FinanceStatisticsVo();
        List<StatCardVo> cards = new ArrayList<>();
        BigDecimal applied = queryMapper.sumAppliedAmount(deptId, range.startDateTime(), range.endExclusiveDateTime());
        cards.add(StatCardVo.of(MetricCodes.FN_APPLIED_MONTH, "本月报销总额（申请额）", "amount",
            ReportRange.amount(applied), applied));
        BigDecimal pending = queryMapper.sumPendingAmount(deptId);
        cards.add(StatCardVo.of(MetricCodes.FN_PENDING_AMOUNT, "待审批报销金额", "amount",
            ReportRange.amount(pending), pending));
        BigDecimal approved = queryMapper.sumApprovedAmount(deptId, range.startDateTime(), range.endExclusiveDateTime());
        cards.add(StatCardVo.of(MetricCodes.FN_APPROVED_MONTH, "本月批准额", "amount",
            ReportRange.amount(approved), approved));
        BigDecimal paid = queryMapper.sumPaidAmount(deptId, range.start(), range.endExclusive());
        cards.add(StatCardVo.of(MetricCodes.FN_PAID_MONTH, "本月已付报销", "amount",
            ReportRange.amount(paid), paid));
        vo.setCards(cards);

        ReportRange year = ReportRange.last12Months();
        List<TrendPointVo> trend = new ArrayList<>();
        Map<String, MonthValueVo> byMonth = new LinkedHashMap<>();
        for (MonthValueVo row : queryMapper.financeTrend(deptId, year.startDateTime(), year.endExclusiveDateTime())) {
            byMonth.put(row.getY() + "-" + row.getM(), row);
        }
        for (LocalDate monthStart = year.start().withDayOfMonth(1);
             monthStart.isBefore(year.endExclusive()); monthStart = monthStart.plusMonths(1)) {
            TrendPointVo point = new TrendPointVo();
            point.setDate(String.format("%d-%02d", monthStart.getYear(), monthStart.getMonthValue()));
            MonthValueVo row = byMonth.get(monthStart.getYear() + "-" + monthStart.getMonthValue());
            point.setValue(row == null ? BigDecimal.ZERO : row.getValue());
            point.setValue2(row == null ? BigDecimal.ZERO : row.getValue2());
            trend.add(point);
        }
        vo.setTrend12m(trend);
        vo.setTypeDistribution(queryMapper.expenseTypeDistribution(deptId, range.start(), range.endExclusive()));
        vo.setDeptTop(queryMapper.expenseTopByDept(deptId, range.start(), range.endExclusive(), TOP_LIMIT));
        vo.setMetrics(metricsFor(ReportType.FINANCE));
        return vo;
    }

    // ---------------------------------------------------------------- 流程看板

    @Override
    public FlowStatisticsVo flow(ReportRangeBo query) {
        Long userId = LoginHelper.getUserId();
        Long deptId = resolveDeptId(query == null ? null : query.getDeptId());
        int timeoutHours = query != null && query.getTimeoutHours() != null && query.getTimeoutHours() > 0
            ? query.getTimeoutHours() : MetricCodes.TIMEOUT_HOURS;
        LocalDateTime timeoutStart = LocalDateTime.now().minusHours(timeoutHours);
        ReportRange week = weekRange();
        ReportRange month = ReportRange.currentMonth();
        ReportRange range = rangeOf(query, month);

        FlowStatisticsVo vo = new FlowStatisticsVo();
        List<StatCardVo> cards = new ArrayList<>();
        long todo = nz(queryMapper.countMyTodo(String.valueOf(userId)));
        cards.add(StatCardVo.of(MetricCodes.FLOW_TODO_COUNT, "我的待办数量", "count",
            String.valueOf(todo), BigDecimal.valueOf(todo)));
        long handled = nz(queryMapper.countHandled(String.valueOf(userId),
            week.startDateTime(), week.endExclusiveDateTime()));
        cards.add(StatCardVo.of(MetricCodes.FLOW_HANDLED_WEEK, "本周我处理的流程数", "count",
            String.valueOf(handled), BigDecimal.valueOf(handled)));
        long timeouts = nz(queryMapper.countTimeout(deptId, timeoutStart));
        cards.add(StatCardVo.of(MetricCodes.FLOW_TIMEOUT_COUNT, "超时未处理流程数", "count",
            String.valueOf(timeouts), BigDecimal.valueOf(timeouts)));
        BigDecimal avg = queryMapper.avgDurationHours(deptId, range.startDateTime(), range.endExclusiveDateTime());
        cards.add(StatCardVo.of(MetricCodes.FLOW_AVG_DURATION_HOURS, "平均处理时长", "hours",
            decimal(avg, 2), avg));
        vo.setCards(cards);

        ReportRange last30 = ReportRange.lastDays(TREND_DAYS);
        List<TrendPointVo> trend = new ArrayList<>();
        Map<LocalDate, DayValueVo> byDay = new LinkedHashMap<>();
        for (DayValueVo row : queryMapper.startTrend(deptId, last30.startDateTime(), last30.endExclusiveDateTime())) {
            byDay.put(row.getD(), row);
        }
        for (LocalDate day = last30.start(); day.isBefore(last30.endExclusive()); day = day.plusDays(1)) {
            TrendPointVo point = new TrendPointVo();
            point.setDate(day.toString());
            DayValueVo row = byDay.get(day);
            point.setValue(row == null ? BigDecimal.ZERO : row.getValue());
            trend.add(point);
        }
        vo.setTrend30(trend);
        vo.setByType(queryMapper.byType(deptId, range.startDateTime(), range.endExclusiveDateTime()));
        vo.setTopSlow(queryMapper.topSlow(deptId, range.startDateTime(), range.endExclusiveDateTime(), TOP_LIMIT));
        vo.setTimeouts(exceptionItems(queryMapper.timeoutList(deptId, timeoutStart, timeoutHours, EXCEPTION_LIMIT)));
        vo.setMetrics(metricsFor(ReportType.FLOW));
        return vo;
    }

    // ---------------------------------------------------------------- 明细与导出

    @Override
    public DetailTableVo details(String type, DetailQueryBo query) {
        ReportType reportType = requireType(type);
        Long deptId = resolveDeptId(query == null ? null : query.getDeptId());
        ReportRange range = rangeOf(query, ReportRange.currentMonth());
        DetailTableVo vo = new DetailTableVo();
        vo.setHeaders(headers(reportType));
        switch (reportType) {
            case HR -> {
                LocalDate start = query == null || query.getStartDate() == null ? null : range.start();
                LocalDate end = start == null ? null : range.endExclusive();
                vo.setRows(maskRows(reportType, queryMapper.detailHr(deptId, start, end,
                    query == null ? 20 : query.safePageSize(), offset(query))));
                vo.setTotal(queryMapper.countDetailHr(deptId, start, end));
            }
            case ATTENDANCE -> {
                vo.setRows(queryMapper.detailAttendance(deptId, range.start(), range.endExclusive(),
                    query == null ? 20 : query.safePageSize(), offset(query)));
                vo.setTotal(queryMapper.countDetailAttendance(deptId, range.start(), range.endExclusive()));
            }
            case FINANCE -> {
                vo.setRows(queryMapper.detailFinance(deptId, range.start(), range.endExclusive(),
                    query == null ? 20 : query.safePageSize(), offset(query)));
                vo.setTotal(queryMapper.countDetailFinance(deptId, range.start(), range.endExclusive()));
            }
            case FLOW -> {
                vo.setRows(queryMapper.detailFlow(deptId, range.startDateTime(), range.endExclusiveDateTime(),
                    query == null ? 20 : query.safePageSize(), offset(query)));
                vo.setTotal(queryMapper.countDetailFlow(deptId, range.startDateTime(), range.endExclusiveDateTime()));
            }
            default -> throw new ServiceException("RP_TYPE_INVALID 不支持的报表类型", 400);
        }
        return vo;
    }

    @Override
    public ReportTable table(ReportType type, ReportRange range, Long deptId, int cap) {
        List<Map<String, Object>> rows = detailRows(type, deptId, range, cap + 1, 0);
        if (rows.size() > cap) {
            throw new ServiceException("RP_EXPORT_LIMIT 导出超过 " + cap + " 行上限，请缩小区间后重试", 400);
        }
        List<HeaderVo> headers = headers(type);
        List<List<String>> out = new ArrayList<>();
        for (Map<String, Object> row : maskRows(type, rows)) {
            List<String> cells = new ArrayList<>();
            for (HeaderVo header : headers) {
                cells.add(cell(row.get(header.getKey())));
            }
            out.add(cells);
        }
        List<String> labels = headers.stream().map(HeaderVo::getLabel).toList();
        return new ReportTable(labels, out);
    }

    private List<Map<String, Object>> detailRows(ReportType type, Long deptId, ReportRange range,
                                                 int limit, int offset) {
        return switch (type) {
            case HR -> queryMapper.detailHr(deptId, null, null, limit, offset);
            case ATTENDANCE -> queryMapper.detailAttendance(deptId, range.start(), range.endExclusive(), limit, offset);
            case FINANCE -> queryMapper.detailFinance(deptId, range.start(), range.endExclusive(), limit, offset);
            case FLOW -> queryMapper.detailFlow(deptId, range.startDateTime(), range.endExclusiveDateTime(), limit, offset);
        };
    }

    @Override
    public Long resolveDeptId(Long requestedDeptId) {
        if (ReportingAccessPolicy.canViewAllDepartments(LoginHelper.isSuperAdmin(), roleKeys())) {
            return requestedDeptId;
        }
        return identityMapper.selectUserDeptId(LoginHelper.getUserId());
    }

    // ---------------------------------------------------------------- helpers

    private List<MetricVo> metricsFor(ReportType type) {
        List<String> codes = MetricCodes.METRICS_BY_TYPE.getOrDefault(type, List.of());
        return metricService.metrics(null).stream().filter(metric -> codes.contains(metric.getMetricCode())).toList();
    }

    private ReportRange rangeOf(ReportRangeBo query, ReportRange fallback) {
        if (query == null || (query.getStartDate() == null && query.getEndDate() == null)) {
            return fallback;
        }
        return ReportRange.of(query.getStartDate(), query.getEndDate());
    }

    private ReportRange weekRange() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        return ReportRange.of(monday, today);
    }

    private int offset(DetailQueryBo query) {
        return query == null ? 0 : (query.safePageNum() - 1) * query.safePageSize();
    }

    private List<WorkItemVo> workItems(List<Map<String, Object>> rows) {
        List<WorkItemVo> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WorkItemVo item = new WorkItemVo();
            item.setId(cell(row.get("id")));
            item.setTitle(cell(row.get("title")));
            item.setBusinessType(cell(row.get("business_type")));
            item.setStatus(cell(row.get("status")));
            item.setTime(fmtTime(row.get("created_at")));
            items.add(item);
        }
        return items;
    }

    private List<WorkEventVo> workEvents(List<Map<String, Object>> rows) {
        List<WorkEventVo> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WorkEventVo item = new WorkEventVo();
            item.setId(cell(row.get("id")));
            item.setTitle(cell(row.get("title")));
            item.setStartTime(fmtTime(row.get("start_time")));
            item.setEndTime(fmtTime(row.get("end_time")));
            items.add(item);
        }
        return items;
    }

    private List<WorkNoticeVo> workNotices(List<Map<String, Object>> rows) {
        List<WorkNoticeVo> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WorkNoticeVo item = new WorkNoticeVo();
            item.setId(cell(row.get("id")));
            item.setTitle(cell(row.get("title")));
            item.setPublishTime(fmtTime(row.get("publish_time")));
            items.add(item);
        }
        return items;
    }

    private List<ExceptionItemVo> exceptionItems(List<Map<String, Object>> rows) {
        List<ExceptionItemVo> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ExceptionItemVo item = new ExceptionItemVo();
            item.setId(cell(row.get("id")));
            item.setLabel(cell(row.get("label")));
            item.setDetail(cell(row.get("detail")));
            item.setTime(cell(row.get("time")));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> maskRows(ReportType type, List<Map<String, Object>> rows) {
        if (type != ReportType.HR) {
            return rows;
        }
        List<Map<String, Object>> masked = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            if (copy.get("phone") != null) {
                copy.put("phone", ReportMasker.maskPhone(cell(copy.get("phone"))));
            }
            if (copy.get("id_card_no") != null) {
                copy.put("id_card_no", ReportMasker.maskIdCard(cell(copy.get("id_card_no"))));
            }
            if (copy.get("email") != null) {
                copy.put("email", ReportMasker.maskEmail(cell(copy.get("email"))));
            }
            masked.add(copy);
        }
        return masked;
    }

    private List<HeaderVo> headers(ReportType type) {
        return switch (type) {
            case HR -> List.of(HeaderVo.of("employee_no", "工号"), HeaderVo.of("name", "姓名"),
                HeaderVo.of("dept_name", "部门"), HeaderVo.of("position_level", "职级"),
                HeaderVo.of("status", "状态"), HeaderVo.of("entry_date", "入职日期"),
                HeaderVo.of("leave_date", "离职日期"), HeaderVo.of("phone", "手机号"),
                HeaderVo.of("id_card_no", "身份证号"), HeaderVo.of("email", "邮箱"));
            case ATTENDANCE -> List.of(HeaderVo.of("attendance_date", "考勤日期"),
                HeaderVo.of("user_name", "姓名"), HeaderVo.of("dept_name", "部门"),
                HeaderVo.of("work_status", "状态"), HeaderVo.of("first_punch_time", "首次打卡"),
                HeaderVo.of("last_punch_time", "末次打卡"), HeaderVo.of("scheduled_minutes", "计划分钟"),
                HeaderVo.of("worked_minutes", "出勤分钟"), HeaderVo.of("late_minutes", "迟到分钟"),
                HeaderVo.of("early_minutes", "早退分钟"), HeaderVo.of("leave_minutes", "请假分钟"),
                HeaderVo.of("overtime_minutes", "加班分钟"), HeaderVo.of("is_abnormal", "异常"));
            case FINANCE -> List.of(HeaderVo.of("reimburse_no", "报销单号"), HeaderVo.of("user_name", "申请人"),
                HeaderVo.of("dept_name", "部门"), HeaderVo.of("expense_type", "费用类型"),
                HeaderVo.of("occur_date", "发生日期"), HeaderVo.of("amount", "金额"),
                HeaderVo.of("status", "状态"));
            case FLOW -> List.of(HeaderVo.of("business_key", "单号"), HeaderVo.of("title", "标题"),
                HeaderVo.of("business_type", "业务类型"), HeaderVo.of("initiator_name", "发起人"),
                HeaderVo.of("dept_name", "部门"), HeaderVo.of("status", "状态"),
                HeaderVo.of("start_time", "发起时间"), HeaderVo.of("end_time", "结束时间"),
                HeaderVo.of("duration_hours", "时长(小时)"));
        };
    }

    private ReportType requireType(String type) {
        ReportType reportType = ReportType.of(type);
        if (reportType == null) {
            throw new ServiceException("RP_TYPE_INVALID 不支持的报表类型：" + type, 400);
        }
        return reportType;
    }

    private List<TrendPointVo> fillMonths(List<MonthValueVo> rows, ReportRange range) {
        List<TrendPointVo> trend = new ArrayList<>();
        Map<String, MonthValueVo> byMonth = new LinkedHashMap<>();
        for (MonthValueVo row : rows) {
            byMonth.put(row.getY() + "-" + row.getM(), row);
        }
        for (LocalDate month = range.start().withDayOfMonth(1);
             month.isBefore(range.endExclusive()); month = month.plusMonths(1)) {
            TrendPointVo point = new TrendPointVo();
            point.setDate(String.format("%d-%02d", month.getYear(), month.getMonthValue()));
            MonthValueVo row = byMonth.get(month.getYear() + "-" + month.getMonthValue());
            point.setValue(row == null ? BigDecimal.ZERO : row.getValue());
            point.setValue2(row == null ? BigDecimal.ZERO : row.getValue2());
            trend.add(point);
        }
        return trend;
    }

    private static BigDecimal ratioRaw(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private static String decimal(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static String cell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String fmtTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(TIME_FORMAT);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIME_FORMAT);
        }
        return String.valueOf(value);
    }

    private static BigDecimal dec(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return BigDecimal.ZERO;
        }
        Object value = row.get(key);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private Set<String> permissions() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getMenuPermission() == null ? Set.of() : loginUser.getMenuPermission();
    }

    private Set<String> roleKeys() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getRolePermission() == null ? Set.of() : loginUser.getRolePermission();
    }
}
