package org.dromara.agentoa.reporting.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.reporting.domain.OaReportExport;
import org.dromara.agentoa.reporting.domain.OaReportMetric;
import org.dromara.agentoa.reporting.domain.OaReportMetricVersion;
import org.dromara.agentoa.reporting.mapper.OaReportExportMapper;
import org.dromara.agentoa.reporting.mapper.OaReportMetricMapper;
import org.dromara.agentoa.reporting.mapper.OaReportMetricVersionMapper;
import org.dromara.agentoa.reporting.mapper.ReportQueryMapper;
import org.dromara.agentoa.reporting.mapper.ReportingIdentityReadMapper;
import org.dromara.agentoa.reporting.service.impl.ReportExportServiceImpl;
import org.dromara.agentoa.reporting.service.impl.ReportMetricServiceImpl;
import org.dromara.agentoa.reporting.service.impl.ReportQueryServiceImpl;
import org.dromara.agentoa.reporting.service.support.ExportFileStore;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.h2.jdbcx.JdbcConnectionPool;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** H2 + MyBatis-Plus 测试环境（真实 Mapper、真实服务实现，导出文件用内存实现）。 */
public final class ReportingTestEnvironment {

    public static final Long USER_EMPLOYEE_A = 100L;
    public static final Long USER_MANAGER_A = 200L;
    public static final Long USER_HR = 300L;
    public static final Long USER_EMPLOYEE_B = 400L;
    public static final Long DEPT_A = 10L;
    public static final Long DEPT_B = 20L;

    private static DataSource dataSource;
    private static TransactionTemplate txTemplate;

    public static OaReportMetricMapper metrics;
    public static OaReportMetricVersionMapper metricVersions;
    public static OaReportExportMapper exports;
    public static ReportQueryMapper queries;
    public static ReportingIdentityReadMapper identity;

    public static ReportMetricServiceImpl metricService;
    public static ReportQueryServiceImpl queryService;
    public static ReportExportServiceImpl exportService;

    public static final Map<Long, byte[]> STORED_FILES = new ConcurrentHashMap<>();
    private static final AtomicLong FILE_IDS = new AtomicLong(900000L);

    private ReportingTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:rptest-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR", "sa", "");
        runSchema();

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        globalConfig.getDbConfig().setIdType(IdType.ASSIGN_ID);
        factoryBean.setGlobalConfig(globalConfig);
        factoryBean.setConfiguration(new MybatisConfiguration());
        SqlSessionTemplate sqlSession;
        try {
            var factory = factoryBean.getObject();
            var configuration = factory.getConfiguration();
            for (Class<?> mapper : List.of(OaReportMetricMapper.class, OaReportMetricVersionMapper.class,
                OaReportExportMapper.class)) {
                configuration.addMapper(mapper);
            }
            configuration.addMapper(ReportQueryMapper.class);
            configuration.addMapper(ReportingIdentityReadMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        metrics = sqlSession.getMapper(OaReportMetricMapper.class);
        metricVersions = sqlSession.getMapper(OaReportMetricVersionMapper.class);
        exports = sqlSession.getMapper(OaReportExportMapper.class);
        queries = sqlSession.getMapper(ReportQueryMapper.class);
        identity = sqlSession.getMapper(ReportingIdentityReadMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);
        bootstrapContext();

        ExportFileStore fileStore = (fileName, content, ownerUserId) -> {
            long id = FILE_IDS.incrementAndGet();
            STORED_FILES.put(id, content);
            return id;
        };
        metricService = new ReportMetricServiceImpl(metrics, metricVersions);
        queryService = new ReportQueryServiceImpl(queries, identity, metricService);
        exportService = new ReportExportServiceImpl(exports, queryService, metricService, fileStore);
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_report_metric", "oa_report_metric_version", "oa_report_export",
            "oa_employee", "oa_attendance_day", "oa_punch_record", "oa_leave_balance", "oa_leave_request",
            "oa_reimburse_request", "oa_expense_item", "oa_expense_type", "oa_payment_record", "oa_flow_instance",
            "oa_calendar_event", "oa_calendar_attendee", "oa_announcement", "oa_announcement_audience",
            "ACT_RU_TASK", "ACT_RU_IDENTITYLINK", "ACT_HI_TASKINST",
            "sys_user", "sys_dept", "sys_role", "sys_user_role")) {
            jdbcTemplate().execute("DELETE FROM " + table);
        }
        STORED_FILES.clear();
    }

    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission, Set<String> roleKeys) {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setNickname("用户" + userId);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setTenantId("000000");
        user.setDeptId(deptId);
        user.setDeptName("测试部门");
        user.setMenuPermission(menuPermission == null ? Set.of() : menuPermission);
        user.setRolePermission(roleKeys == null ? Set.of() : roleKeys);
        var param = new cn.dev33.satoken.stp.parameter.SaLoginParameter();
        param.setExtra(LoginHelper.CLIENT_KEY, "test-client");
        LoginHelper.login(user, param);
    }

    public static void logout() {
        try {
            cn.dev33.satoken.stp.StpUtil.logout();
        } catch (Exception ignored) {
            // 未登录时忽略
        }
    }

    public static void seedUser(Long userId, Long deptId, String nickname) {
        jdbcTemplate().update("INSERT INTO sys_user(user_id, dept_id, user_name, nick_name) VALUES(?,?,?,?)",
            userId, deptId, "u" + userId, nickname);
    }

    public static void seedDept(Long deptId, String name) {
        jdbcTemplate().update("INSERT INTO sys_dept(dept_id, dept_name) VALUES(?,?)", deptId, name);
    }

    public static void seedEmployee(long id, Long userId, String employeeNo, Long deptId,
                                    String status, LocalDate entryDate, LocalDate leaveDate) {
        jdbcTemplate().update("INSERT INTO oa_employee(id, user_id, employee_no, name, dept_id, position_level, "
                + "status, entry_date, leave_date, phone, id_card_no, email) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id, userId, employeeNo, "员工" + employeeNo, deptId, "P" + (id % 3), status, entryDate, leaveDate,
            "1380000" + String.format("%04d", id), "11010119900101" + String.format("%04d", id),
            "u" + id + "@example.com");
    }

    public static void seedAttendanceDay(long id, Long userId, Long deptId, LocalDate day,
                                         int scheduled, int worked, int late, int leave, int overtime,
                                         boolean abnormal) {
        jdbcTemplate().update("INSERT INTO oa_attendance_day(id, user_id, dept_id, attendance_date, work_status, "
                + "scheduled_minutes, worked_minutes, late_minutes, leave_minutes, overtime_minutes, is_abnormal, "
                + "abnormal_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id, userId, deptId, day, 1, scheduled, worked, late, leave, overtime, abnormal ? 1 : 0,
            abnormal ? "缺卡" : null);
    }

    public static void seedPunch(long id, Long userId, LocalDate day, LocalDateTime time, int type) {
        jdbcTemplate().update("INSERT INTO oa_punch_record(id, user_id, punch_date, punch_time, punch_type) "
                + "VALUES(?,?,?,?,?)",
            id, userId, day, time, type);
    }

    public static void seedBalance(long id, Long userId, int year, String leaveType,
                                   int total, int frozen, int used) {
        jdbcTemplate().update("INSERT INTO oa_leave_balance(id, user_id, year, leave_type, "
                + "total_minutes, frozen_minutes, used_minutes) VALUES(?,?,?,?,?,?,?)",
            id, userId, year, leaveType, total, frozen, used);
    }

    public static void seedLeaveRequest(long id, Long userId, String leaveType, int status,
                                        LocalDateTime start, int minutes) {
        jdbcTemplate().update("INSERT INTO oa_leave_request(id, user_id, leave_type, status, start_time, end_time, "
                + "duration_minutes) VALUES(?,?,?,?,?,?,?)",
            id, userId, leaveType, status, start, start.plusHours(8), minutes);
    }

    public static void seedFlowInstance(long id, String businessType, Long initiatorUserId, Long initiatorDeptId,
                                        int status, LocalDateTime startTime, LocalDateTime endTime,
                                        Long durationMillis, String title) {
        jdbcTemplate().update("INSERT INTO oa_flow_instance(id, definition_id, definition_version_id, "
                + "form_version_id, business_type, business_id, title, initiator_user_id, initiator_name, "
                + "initiator_dept_id, status, start_time, end_time, duration) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, 1L, 1L, 1L, businessType, id, title == null ? businessType + "-" + id : title,
            initiatorUserId, "用户" + initiatorUserId, initiatorDeptId, status, startTime, endTime, durationMillis);
    }

    public static void seedReimburse(long id, String reimburseNo, Long userId, int status, Long flowInstanceId,
                                     String amount) {
        jdbcTemplate().update("INSERT INTO oa_reimburse_request(id, reimburse_no, user_id, status, flow_instance_id, "
                + "total_amount) VALUES(?,?,?,?,?,?)",
            id, reimburseNo, userId, status, flowInstanceId, amount);
    }

    public static void seedExpenseItem(long id, Long reimburseId, String expenseType, LocalDate occurDate,
                                       String amount) {
        jdbcTemplate().update("INSERT INTO oa_expense_item(id, reimburse_id, expense_type, occur_date, amount) "
                + "VALUES(?,?,?,?,?)",
            id, reimburseId, expenseType, occurDate, amount);
    }

    public static void seedPayment(long id, Long reimburseId, String amount, LocalDate payDate, int status) {
        jdbcTemplate().update("INSERT INTO oa_payment_record(id, payment_no, reimburse_id, amount, pay_date, "
                + "pay_status, operator_id) VALUES(?,?,?,?,?,?,?)",
            id, "P" + id, reimburseId, amount, payDate, status, 1L);
    }

    public static void seedTodoTask(String taskId, String procInstId, Long assigneeUserId) {
        jdbcTemplate().update("INSERT INTO ACT_RU_TASK(ID_, PROC_INST_ID_, NAME_, ASSIGNEE_, CREATE_TIME_) "
                + "VALUES(?,?,?,?,?)",
            taskId, procInstId, "审批", String.valueOf(assigneeUserId), LocalDateTime.now());
    }

    public static void seedCandidateTask(String taskId, String procInstId, Long candidateUserId) {
        jdbcTemplate().update("INSERT INTO ACT_RU_TASK(ID_, PROC_INST_ID_, NAME_, CREATE_TIME_) VALUES(?,?,?,?)",
            taskId, procInstId, "待认领", LocalDateTime.now());
        jdbcTemplate().update("INSERT INTO ACT_RU_IDENTITYLINK(ID_, TYPE_, USER_ID_, TASK_ID_) VALUES(?,?,?,?)",
            "L" + taskId, "candidate", String.valueOf(candidateUserId), taskId);
    }

    public static void seedHistoricTask(String taskId, String procInstId, Long assigneeUserId,
                                        LocalDateTime endTime) {
        jdbcTemplate().update("INSERT INTO ACT_HI_TASKINST(ID_, PROC_INST_ID_, NAME_, ASSIGNEE_, START_TIME_, "
                + "END_TIME_) VALUES(?,?,?,?,?,?)",
            taskId, procInstId, "审批", String.valueOf(assigneeUserId), endTime.minusHours(2), endTime);
    }

    public static void seedEvent(long id, Long organizerId, LocalDateTime start, LocalDateTime end) {
        jdbcTemplate().update("INSERT INTO oa_calendar_event(id, title, start_time, end_time, organizer_id) "
                + "VALUES(?,?,?,?,?)",
            id, "日程" + id, start, end, organizerId);
    }

    public static void seedAnnouncement(long id, String title, int status, LocalDateTime publishTime, Long audience) {
        jdbcTemplate().update("INSERT INTO oa_announcement(id, title, content, publisher_id, publish_time, status) "
                + "VALUES(?,?,?,?,?,?)",
            id, title, "内容", 1L, publishTime, status);
        if (audience != null) {
            jdbcTemplate().update("INSERT INTO oa_announcement_audience(notice_id, user_id) VALUES(?,?)",
                id, audience);
        }
    }

    public static void seedMetric(String code, String name, String category, int currentVersion, String definition) {
        long id = Math.abs((long) code.hashCode());
        jdbcTemplate().update("INSERT INTO oa_report_metric(id, metric_code, metric_name, category, unit, "
                + "current_version, status) VALUES(?,?,?,?,?,?,?)",
            id, code, name, category, "count", currentVersion, 1);
        jdbcTemplate().update("INSERT INTO oa_report_metric_version(id, metric_code, version, definition, status) "
                + "VALUES(?,?,?,?,?)",
            id, code, currentVersion, definition, 1);
    }

    /** 按 docs/11–17 指标编码播种模块 8 全部字典指标。 */
    public static void seedAllMetrics() {
        seedMetric("hr.active_count", "在职总人数", "hr", 1, "{\"statusFilter\":\"active\"}");
        seedMetric("hr.new_hire_month", "本月新入职", "hr", 1, "{\"timeField\":\"entry_date\"}");
        seedMetric("hr.leave_month", "本月离职", "hr", 1, "{\"timeField\":\"leave_date\"}");
        seedMetric("hr.probation_count", "试用期人数", "hr", 1, "{\"statusFilter\":\"PROBATION\"}");
        seedMetric("hr.dept_distribution", "部门人数分布", "hr", 1, "{\"orgFilter\":\"dept_id\"}");
        seedMetric("att.rate_today", "今日出勤率", "attendance", 1, "{\"formula\":\"worked/scheduled\"}");
        seedMetric("att.late_week", "本周迟到次数", "attendance", 1, "{\"timeField\":\"attendance_date\"}");
        seedMetric("att.overtime_month", "本月加班时长", "attendance", 1, "{\"timeField\":\"attendance_date\"}");
        seedMetric("att.leave_days_month", "本月请假天数", "attendance", 1, "{\"formula\":\"minutes/480\"}");
        seedMetric("fn.applied_month", "本月申请额", "finance", 1, "{\"timeField\":\"start_time\"}");
        seedMetric("fn.approved_month", "本月批准额", "finance", 1, "{\"timeField\":\"end_time\"}");
        seedMetric("fn.paid_month", "本月实付额", "finance", 1, "{\"timeField\":\"pay_date\"}");
        seedMetric("fn.pending_amount", "在途待审批金额", "finance", 1, "{\"statusFilter\":\"2\"}");
        seedMetric("flow.todo_count", "我的待办数", "workflow", 1, "{\"dataSource\":\"ACT_RU_TASK\"}");
        seedMetric("flow.handled_week", "本周处理数", "workflow", 1, "{\"dataSource\":\"ACT_HI_TASKINST\"}");
        seedMetric("flow.timeout_count", "超时未处理数", "workflow", 1, "{\"thresholdHours\":24}");
        seedMetric("flow.avg_duration_hours", "平均处理时长", "workflow", 1, "{\"formula\":\"AVG(duration)\"}");
        seedMetric("flow.by_type", "流程类型分布", "workflow", 1, "{\"groupBy\":\"business_type\"}");
    }

    public static long exportCount() {
        return jdbcTemplate().queryForObject("SELECT COUNT(*) FROM oa_report_export", Long.class);
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    /** 迷你 Spring 上下文：供 JsonUtils 使用 */
    private static void bootstrapContext() {
        org.springframework.context.support.GenericApplicationContext context =
            new org.springframework.context.support.GenericApplicationContext();
        context.registerBean("objectMapper", com.fasterxml.jackson.databind.ObjectMapper.class,
            () -> new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
        context.refresh();
        new org.dromara.common.core.utils.SpringUtils().setApplicationContext(context);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("rp-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append('\n');
                }
            }
            for (String part : sql.toString().split(";")) {
                if (!part.isBlank()) {
                    statement.execute(part);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create test schema", e);
        }
    }

    /** 测试断言辅助：导出任务行。 */
    public static Map<String, Object> exportRow(long id) {
        return jdbcTemplate().queryForMap("SELECT * FROM oa_report_export WHERE id = ?", id);
    }

    public static Date now() {
        return new Date();
    }
}
