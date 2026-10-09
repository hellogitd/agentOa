package org.dromara.agentoa.collaboration.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.domain.OaMeetingRoom;
import org.dromara.agentoa.collaboration.domain.OaRoomBooking;
import org.dromara.agentoa.collaboration.domain.OaTask;
import org.dromara.agentoa.collaboration.mapper.CalendarAttendeeMapper;
import org.dromara.agentoa.collaboration.mapper.CollaborationIdentityReadMapper;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.dromara.agentoa.collaboration.mapper.OaMeetingRoomMapper;
import org.dromara.agentoa.collaboration.mapper.OaRoomBookingMapper;
import org.dromara.agentoa.collaboration.mapper.OaTaskActivityMapper;
import org.dromara.agentoa.collaboration.mapper.OaTaskMapper;
import org.dromara.agentoa.collaboration.mapper.TaskMemberMapper;
import org.dromara.agentoa.collaboration.service.impl.CalendarEventServiceImpl;
import org.dromara.agentoa.collaboration.service.impl.MeetingRoomServiceImpl;
import org.dromara.agentoa.collaboration.service.impl.RoomBookingServiceImpl;
import org.dromara.agentoa.collaboration.service.impl.TaskServiceImpl;
import org.dromara.agentoa.collaboration.service.support.CollaborationOutboxWriter;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
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
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** H2 + MyBatis-Plus 测试环境（真实 Mapper、真实服务实现） */
public final class CollaborationTestEnvironment {

    public static final Long USER_ORGANIZER = 100L;
    public static final Long USER_ATTENDEE = 200L;
    public static final Long USER_STRANGER = 300L;
    public static final Long DEPT_A = 10L;
    public static final Long DEPT_B = 20L;

    private static DataSource dataSource;
    private static TransactionTemplate txTemplate;

    public static OaCalendarEventMapper events;
    public static CalendarAttendeeMapper attendees;
    public static OaMeetingRoomMapper rooms;
    public static OaRoomBookingMapper bookings;
    public static OaTaskMapper tasks;
    public static OaTaskActivityMapper activities;
    public static TaskMemberMapper members;
    public static CollaborationIdentityReadMapper identity;
    public static OaIdempotencyMapper idempotencies;

    public static CalendarEventServiceImpl eventService;
    public static MeetingRoomServiceImpl roomService;
    public static RoomBookingServiceImpl bookingService;
    public static TaskServiceImpl taskService;
    public static CollaborationOutboxWriter outboxWriter;
    public static IdempotencyGuard idempotencyGuard;

    private CollaborationTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:collabtest-" + System.nanoTime()
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
            for (Class<?> mapper : List.of(OaCalendarEventMapper.class, OaMeetingRoomMapper.class,
                OaRoomBookingMapper.class, OaTaskMapper.class, OaTaskActivityMapper.class,
                OaIdempotencyMapper.class)) {
                configuration.addMapper(mapper);
            }
            configuration.addMapper(CalendarAttendeeMapper.class);
            configuration.addMapper(TaskMemberMapper.class);
            configuration.addMapper(CollaborationIdentityReadMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        events = sqlSession.getMapper(OaCalendarEventMapper.class);
        attendees = sqlSession.getMapper(CalendarAttendeeMapper.class);
        rooms = sqlSession.getMapper(OaMeetingRoomMapper.class);
        bookings = sqlSession.getMapper(OaRoomBookingMapper.class);
        tasks = sqlSession.getMapper(OaTaskMapper.class);
        activities = sqlSession.getMapper(OaTaskActivityMapper.class);
        members = sqlSession.getMapper(TaskMemberMapper.class);
        identity = sqlSession.getMapper(CollaborationIdentityReadMapper.class);
        idempotencies = sqlSession.getMapper(OaIdempotencyMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);
        bootstrapContext();

        outboxWriter = new CollaborationOutboxWriter(new JdbcTemplate(dataSource));
        idempotencyGuard = new IdempotencyGuard(idempotencies);
        eventService = new CalendarEventServiceImpl(events, attendees, identity, rooms, bookings, outboxWriter);
        roomService = new MeetingRoomServiceImpl(rooms, bookings);
        bookingService = new RoomBookingServiceImpl(bookings, rooms, events, identity, idempotencyGuard, outboxWriter);
        taskService = new TaskServiceImpl(tasks, activities, members, identity, outboxWriter);
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_calendar_event", "oa_calendar_attendee", "oa_meeting_room",
            "oa_room_booking", "oa_task", "oa_task_member", "oa_task_activity",
            "oa_idempotency", "sys_outbox", "sys_user", "sys_dept", "sys_role", "sys_user_role")) {
            jdbcTemplate().execute("DELETE FROM " + table);
        }
    }

    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission) {
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
    }

    public static void seedDept(Long deptId, String name) {
        jdbcTemplate().update("INSERT INTO sys_dept(dept_id, dept_name) VALUES(?,?)", deptId, name);
    }

    public static OaMeetingRoom seedRoom(long id, String name, int status) {
        OaMeetingRoom room = new OaMeetingRoom();
        room.setId(id);
        room.setName(name);
        room.setCapacity(8);
        room.setStatus(status);
        room.setDelFlag(0);
        room.setCreateTime(new Date());
        rooms.insert(room);
        return room;
    }

    public static OaCalendarEvent seedEvent(long id, long organizerId, int visibility, Date start, Date end) {
        OaCalendarEvent event = new OaCalendarEvent();
        event.setId(id);
        event.setLockVersion(0);
        event.setTitle("测试日程" + id);
        event.setEventType(1);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setIsAllDay(0);
        event.setOrganizerId(organizerId);
        event.setVisibility(visibility);
        event.setRemindMinutes(15);
        event.setStatus(1);
        event.setCreateTime(new Date());
        event.setDelFlag(0);
        events.insert(event);
        return event;
    }

    public static OaTask seedTask(long id, long assignerId, long assigneeId, int status) {
        OaTask task = new OaTask();
        task.setId(id);
        task.setLockVersion(0);
        task.setTitle("测试任务" + id);
        task.setAssignerId(assignerId);
        task.setAssigneeId(assigneeId);
        task.setPriority(2);
        task.setStatus(status);
        task.setProgress(status == 4 ? 100 : 0);
        task.setCreateTime(new Date());
        task.setDelFlag(0);
        tasks.insert(task);
        return task;
    }

    public static long outboxCount() {
        return jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class);
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    /** 迷你 Spring 上下文：供 JsonUtils（Hutool SpringUtil）使用 */
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
                new ClassPathResource("collab-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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
}
