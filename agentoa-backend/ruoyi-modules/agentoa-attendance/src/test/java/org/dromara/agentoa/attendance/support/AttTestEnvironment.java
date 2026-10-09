package org.dromara.agentoa.attendance.support;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.github.linpeilie.Converter;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;
import org.dromara.agentoa.attendance.domain.OaAttendanceMember;
import org.dromara.agentoa.attendance.domain.OaCalendar;
import org.dromara.agentoa.attendance.domain.OaCorrection;
import org.dromara.agentoa.attendance.domain.OaHoliday;
import org.dromara.agentoa.attendance.domain.OaLeaveBalance;
import org.dromara.agentoa.attendance.domain.OaLeaveLedger;
import org.dromara.agentoa.attendance.domain.OaLeaveType;
import org.dromara.agentoa.attendance.domain.OaOvertime;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.mapper.OaAttendanceDayMapper;
import org.dromara.agentoa.attendance.mapper.OaAttendanceGroupMapper;
import org.dromara.agentoa.attendance.mapper.OaAttendanceMemberMapper;
import org.dromara.agentoa.attendance.mapper.OaCalendarMapper;
import org.dromara.agentoa.attendance.mapper.OaCorrectionMapper;
import org.dromara.agentoa.attendance.mapper.OaHolidayMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveBalanceMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveBatchAllocationMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveBatchMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveLedgerMapper;
import org.dromara.agentoa.attendance.mapper.OaLeaveTypeMapper;
import org.dromara.agentoa.attendance.mapper.OaOvertimeMapper;
import org.dromara.agentoa.attendance.mapper.OaPunchRecordMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftAssignmentMapper;
import org.dromara.agentoa.attendance.service.impl.AttendanceDayServiceImpl;
import org.dromara.agentoa.attendance.service.impl.LeaveBalanceServiceImpl;
import org.dromara.agentoa.attendance.service.impl.PunchServiceImpl;
import org.dromara.agentoa.attendance.service.support.AttendanceDurationCalculator;
import org.dromara.agentoa.attendance.service.support.AttendanceFlowListener;
import org.dromara.agentoa.attendance.service.support.ScheduleResolver;
import org.dromara.agentoa.attendance.service.support.WorkTimeCalculator;
import org.dromara.agentoa.hr.domain.OaIdempotency;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.workflow.domain.OaCorrectionRequest;
import org.dromara.agentoa.workflow.domain.OaLeaveRequest;
import org.dromara.agentoa.workflow.domain.OaOvertimeRequest;
import org.dromara.agentoa.workflow.mapper.OaCorrectionRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaLeaveRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaOvertimeRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.support.DurationCalculators;
import org.dromara.agentoa.workflow.service.support.FlowEventListenerRegistry;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.h2.jdbcx.JdbcConnectionPool;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * H2 + MyBatis-Plus 测试基座：真实 Mapper、真实服务实现、真实事务模板。
 */
public final class AttTestEnvironment {

    public static final Long USER_EMPLOYEE = 200L;
    public static final Long USER_OTHER = 300L;
    public static final Long USER_HR = 100L;
    public static final Long DEPT_A = 10L;

    private static DataSource dataSource;
    private static SqlSessionTemplate sqlSession;
    private static TransactionTemplate txTemplate;

    public static OaShiftMapper shifts;
    public static OaAttendanceGroupMapper groups;
    public static OaAttendanceMemberMapper members;
    public static OaCalendarMapper calendars;
    public static OaHolidayMapper holidays;
    public static OaPunchRecordMapper punches;
    public static OaAttendanceDayMapper days;
    public static OaLeaveTypeMapper leaveTypes;
    public static OaLeaveBalanceMapper balances;
    public static OaLeaveBatchMapper batches;
    public static OaLeaveBatchAllocationMapper batchAllocations;
    public static OaLeaveLedgerMapper ledgers;
    public static OaOvertimeMapper overtimes;
    public static OaCorrectionMapper corrections;
    public static OaLeaveRequestMapper leaveRequests;
    public static OaOvertimeRequestMapper overtimeRequests;
    public static OaCorrectionRequestMapper correctionRequests;
    public static WorkflowIdentityReadMapper identity;
    public static OaIdempotencyMapper idempotencies;
    public static OaShiftAssignmentMapper shiftAssignments;

    public static ScheduleResolver scheduleResolver;
    public static WorkTimeCalculator timeCalculator;
    public static LeaveBalanceServiceImpl balanceService;
    public static AttendanceDayServiceImpl dayService;
    public static PunchServiceImpl punchService;
    public static AttendanceFlowListener flowListener;
    public static AttendanceDurationCalculator durationCalculator;
    public static FlowEventListenerRegistry listenerRegistry;
    public static DurationCalculators durationCalculators;

    private AttTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:atttest-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR", "sa", "");
        runSchema();

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        globalConfig.getDbConfig().setIdType(IdType.ASSIGN_ID);
        factoryBean.setGlobalConfig(globalConfig);
        factoryBean.setConfiguration(new MybatisConfiguration());
        try {
            var factory = factoryBean.getObject();
            var configuration = factory.getConfiguration();
            for (Class<?> mapper : List.of(OaShiftMapper.class, OaAttendanceGroupMapper.class,
                OaAttendanceMemberMapper.class, OaCalendarMapper.class, OaHolidayMapper.class,
                OaPunchRecordMapper.class, OaAttendanceDayMapper.class, OaLeaveTypeMapper.class,
                OaLeaveBalanceMapper.class, OaLeaveLedgerMapper.class, OaOvertimeMapper.class,
                OaCorrectionMapper.class, OaLeaveRequestMapper.class, OaOvertimeRequestMapper.class,
                OaCorrectionRequestMapper.class, OaIdempotencyMapper.class, OaShiftAssignmentMapper.class,
                OaLeaveBatchMapper.class, OaLeaveBatchAllocationMapper.class)) {
                configuration.addMapper(mapper);
            }
            configuration.addMapper(WorkflowIdentityReadMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        shifts = sqlSession.getMapper(OaShiftMapper.class);
        groups = sqlSession.getMapper(OaAttendanceGroupMapper.class);
        members = sqlSession.getMapper(OaAttendanceMemberMapper.class);
        calendars = sqlSession.getMapper(OaCalendarMapper.class);
        holidays = sqlSession.getMapper(OaHolidayMapper.class);
        punches = sqlSession.getMapper(OaPunchRecordMapper.class);
        days = sqlSession.getMapper(OaAttendanceDayMapper.class);
        leaveTypes = sqlSession.getMapper(OaLeaveTypeMapper.class);
        balances = sqlSession.getMapper(OaLeaveBalanceMapper.class);
        batches = sqlSession.getMapper(OaLeaveBatchMapper.class);
        batchAllocations = sqlSession.getMapper(OaLeaveBatchAllocationMapper.class);
        ledgers = sqlSession.getMapper(OaLeaveLedgerMapper.class);
        overtimes = sqlSession.getMapper(OaOvertimeMapper.class);
        corrections = sqlSession.getMapper(OaCorrectionMapper.class);
        leaveRequests = sqlSession.getMapper(OaLeaveRequestMapper.class);
        overtimeRequests = sqlSession.getMapper(OaOvertimeRequestMapper.class);
        correctionRequests = sqlSession.getMapper(OaCorrectionRequestMapper.class);
        idempotencies = sqlSession.getMapper(OaIdempotencyMapper.class);
        shiftAssignments = sqlSession.getMapper(OaShiftAssignmentMapper.class);
        identity = sqlSession.getMapper(WorkflowIdentityReadMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        bootstrapConverter();

        scheduleResolver = new ScheduleResolver(members, groups, shifts, calendars, holidays, shiftAssignments);
        timeCalculator = new WorkTimeCalculator();
        balanceService = new LeaveBalanceServiceImpl(leaveTypes, balances, ledgers, batches, batchAllocations);
        dayService = new AttendanceDayServiceImpl(days, punches, overtimes, leaveRequests,
            scheduleResolver, timeCalculator, identity);
        listenerRegistry = new FlowEventListenerRegistry();
        durationCalculators = new DurationCalculators();
        IdempotencyGuard idempotencyGuard = new IdempotencyGuard(idempotencies);
        punchService = new PunchServiceImpl(punches, days, identity, scheduleResolver, timeCalculator,
            dayService, idempotencyGuard) {
            @Override
            protected LocalDateTime currentTime() {
                return fixedNow == null ? LocalDateTime.now() : fixedNow;
            }
        };
        flowListener = new AttendanceFlowListener(listenerRegistry, leaveRequests, overtimeRequests,
            correctionRequests, leaveTypes, overtimes, corrections, punches, balanceService, dayService);
        flowListener.register();
        durationCalculator = new AttendanceDurationCalculator(scheduleResolver, timeCalculator, durationCalculators);
        durationCalculator.register();
    }

    /** 测试固定服务器时间；null 表示使用真实时钟 */
    public static LocalDateTime fixedNow;

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_shift", "oa_attendance_group", "oa_attendance_member", "oa_calendar",
            "oa_holiday", "oa_punch_record", "oa_attendance_day", "oa_leave_type", "oa_leave_balance",
            "oa_leave_ledger", "oa_overtime", "oa_correction", "oa_leave_request", "oa_overtime_request",
              "oa_correction_request", "oa_idempotency", "oa_shift_assignment", "sys_user", "oa_employee",
              "oa_leave_batch", "oa_leave_batch_allocation")) {
            jdbcTemplate().execute("DELETE FROM " + table);
        }
        fixedNow = null;
    }

    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission) {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setNickname("员工" + userId);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setTenantId("000000");
        user.setDeptId(deptId);
        user.setDeptName("测试部门");
        user.setMenuPermission(menuPermission == null ? Set.of() : menuPermission);
        user.setRolePermission(Set.of());
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
        jdbcTemplate().update("INSERT INTO oa_employee(id, user_id, employee_no, name, dept_id, status) VALUES(?,?,?,?,?,?)",
            userId, userId, "E" + userId, nickname, deptId, "ACTIVE");
    }

    public static OaShift seedShift(long id, String code, LocalTime start, LocalTime end,
                                    LocalTime restStart, LocalTime restEnd, int crossDay,
                                    int flexible, int grace, int windowStart, int windowEnd) {
        OaShift shift = new OaShift();
        shift.setId(id);
        shift.setShiftCode(code);
        shift.setShiftName(code);
        shift.setWorkStartTime(start);
        shift.setWorkEndTime(end);
        shift.setRestStartTime(restStart);
        shift.setRestEndTime(restEnd);
        shift.setIsCrossDay(crossDay);
        shift.setFlexibleMinutes(flexible);
        shift.setGraceMinutes(grace);
        shift.setPunchWindowStart(windowStart);
        shift.setPunchWindowEnd(windowEnd);
        shift.setStatus("0");
        shifts.insert(shift);
        return shift;
    }

    public static OaAttendanceGroup seedGroup(long id, String code, long shiftId, String workDays) {
        OaAttendanceGroup group = new OaAttendanceGroup();
        group.setId(id);
        group.setGroupCode(code);
        group.setGroupName(code);
        group.setShiftId(shiftId);
        group.setWorkDays(workDays);
        group.setEffectiveDate(LocalDate.of(2026, 1, 1));
        group.setStatus("0");
        groups.insert(group);
        return group;
    }

    public static void seedMember(long id, long groupId, long userId, LocalDate validFrom, LocalDate validTo) {
        OaAttendanceMember member = new OaAttendanceMember();
        member.setId(id);
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setValidFrom(validFrom);
        member.setValidTo(validTo);
        member.setCreateTime(new Date());
        members.insert(member);
    }

    public static void seedLeaveType(long id, String code, String name, int quotaLimited) {
        OaLeaveType type = new OaLeaveType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setQuotaLimited(quotaLimited);
        type.setDefaultMinutes(0);
        type.setStatus("0");
        leaveTypes.insert(type);
    }

    public static OaLeaveRequest seedLeaveRequest(long id, long userId, String leaveType,
                                                  LocalDateTime start, LocalDateTime end,
                                                  int minutes, int status, int submissionNo) {
        OaLeaveRequest request = new OaLeaveRequest();
        request.setId(id);
        request.setUserId(userId);
        request.setEmployeeId(userId);
        request.setLeaveType(leaveType);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setDurationMinutes(minutes);
        request.setStatus(status);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(0);
        leaveRequests.insert(request);
        return request;
    }

    public static OaOvertimeRequest seedOvertimeRequest(long id, long userId, LocalDate date,
                                                        LocalDateTime start, LocalDateTime end,
                                                        int minutes, int status) {
        OaOvertimeRequest request = new OaOvertimeRequest();
        request.setId(id);
        request.setUserId(userId);
        request.setEmployeeId(userId);
        request.setOvertimeDate(date);
        request.setOvertimeType("weekday");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setDurationMinutes(minutes);
        request.setStatus(status);
        request.setSubmissionNo(1);
        request.setLockVersion(0);
        overtimeRequests.insert(request);
        return request;
    }

    public static OaCorrectionRequest seedCorrectionRequest(long id, long userId, LocalDate date,
                                                            int punchType, LocalDateTime correctedTime,
                                                            int status) {
        OaCorrectionRequest request = new OaCorrectionRequest();
        request.setId(id);
        request.setUserId(userId);
        request.setEmployeeId(userId);
        request.setAttendanceDate(date);
        request.setPunchType(punchType);
        request.setCorrectedTime(correctedTime);
        request.setReason("忘记打卡");
        request.setStatus(status);
        request.setSubmissionNo(1);
        request.setLockVersion(0);
        correctionRequests.insert(request);
        return request;
    }

    public static org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("att-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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

    private static void bootstrapConverter() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(Converter.class, ReflectiveConverter::new);
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    /** 反射式 Bean 属性转换器（MapStruct 未生成代码时专用） */
    public static class ReflectiveConverter extends Converter {

        @Override
        public <S, T> T convert(S source, Class<T> target) {
            if (source == null) {
                return null;
            }
            try {
                T instance = target.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(source, instance);
                return instance;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <S, T> T convert(S source, T target) {
            if (source != null && target != null) {
                BeanUtil.copyProperties(source, target);
            }
            return target;
        }

        @Override
        public <S, T> List<T> convert(List<S> sourceList, Class<T> target) {
            List<T> result = new ArrayList<>();
            if (sourceList != null) {
                for (S source : sourceList) {
                    result.add(convert(source, target));
                }
            }
            return result;
        }

        @Override
        public <T> T convert(Map<String, Object> map, Class<T> target) {
            try {
                T instance = target.getDeclaredConstructor().newInstance();
                if (map != null) {
                    BeanUtil.copyProperties(map, instance);
                }
                return instance;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
