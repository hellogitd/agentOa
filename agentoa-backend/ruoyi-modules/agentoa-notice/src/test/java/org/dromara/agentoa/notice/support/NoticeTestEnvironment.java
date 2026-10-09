package org.dromara.agentoa.notice.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.notice.domain.OaAnnouncement;
import org.dromara.agentoa.notice.domain.OaNoticeTemplate;
import org.dromara.agentoa.notice.mapper.NoticeAudienceMapper;
import org.dromara.agentoa.notice.mapper.NoticeIdentityReadMapper;
import org.dromara.agentoa.notice.mapper.NoticeOutboxMapper;
import org.dromara.agentoa.notice.mapper.NoticePreferenceMapper;
import org.dromara.agentoa.notice.mapper.OaAnnouncementMapper;
import org.dromara.agentoa.notice.mapper.OaAnnouncementReadMapper;
import org.dromara.agentoa.notice.mapper.OaNoticeTemplateMapper;
import org.dromara.agentoa.notice.mapper.OaScheduledPushMapper;
import org.dromara.agentoa.notice.mapper.OaScheduledPushRunMapper;
import org.dromara.agentoa.notice.service.impl.AnnouncementServiceImpl;
import org.dromara.agentoa.notice.service.impl.NoticeTemplateServiceImpl;
import org.dromara.agentoa.notice.service.impl.OutboxAdminServiceImpl;
import org.dromara.agentoa.notice.service.impl.PreferenceServiceImpl;
import org.dromara.agentoa.notice.service.impl.ScheduledPushServiceImpl;
import org.dromara.agentoa.notice.service.support.NoticeAudienceResolver;
import org.dromara.agentoa.notice.service.support.NoticeOutboxWriter;
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

/**
 * H2 + MyBatis-Plus 测试环境（真实 Mapper、真实服务实现）。
 */
public final class NoticeTestEnvironment {

    public static final Long USER_HR = 100L;
    public static final Long USER_EMPLOYEE = 200L;
    public static final Long USER_OTHER = 300L;
    public static final Long DEPT_A = 10L;
    public static final Long DEPT_B = 20L;

    private static DataSource dataSource;
    private static TransactionTemplate txTemplate;

    public static OaAnnouncementMapper announcements;
    public static OaAnnouncementReadMapper reads;
    public static NoticeAudienceMapper audience;
    public static NoticePreferenceMapper preferences;
    public static NoticeIdentityReadMapper identity;
    public static NoticeOutboxMapper outbox;
    public static OaIdempotencyMapper idempotencies;
    public static OaNoticeTemplateMapper templates;
    public static OaScheduledPushMapper pushes;
    public static OaScheduledPushRunMapper pushRuns;

    public static AnnouncementServiceImpl announcementService;
    public static PreferenceServiceImpl preferenceService;
    public static OutboxAdminServiceImpl outboxAdminService;
    public static NoticeAudienceResolver audienceResolver;
    public static NoticeTemplateServiceImpl templateService;
    public static ScheduledPushServiceImpl scheduledPushService;
    public static NoticeOutboxWriter outboxWriter;
    public static IdempotencyGuard idempotencyGuard;

    private NoticeTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:nttest-" + System.nanoTime()
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
            for (Class<?> mapper : List.of(OaAnnouncementMapper.class, OaAnnouncementReadMapper.class,
                OaIdempotencyMapper.class, OaNoticeTemplateMapper.class, OaScheduledPushMapper.class,
                OaScheduledPushRunMapper.class)) {
                configuration.addMapper(mapper);
            }
            configuration.addMapper(NoticeAudienceMapper.class);
            configuration.addMapper(NoticePreferenceMapper.class);
            configuration.addMapper(NoticeIdentityReadMapper.class);
            configuration.addMapper(NoticeOutboxMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        announcements = sqlSession.getMapper(OaAnnouncementMapper.class);
        reads = sqlSession.getMapper(OaAnnouncementReadMapper.class);
        audience = sqlSession.getMapper(NoticeAudienceMapper.class);
        preferences = sqlSession.getMapper(NoticePreferenceMapper.class);
        identity = sqlSession.getMapper(NoticeIdentityReadMapper.class);
        outbox = sqlSession.getMapper(NoticeOutboxMapper.class);
        idempotencies = sqlSession.getMapper(OaIdempotencyMapper.class);
        templates = sqlSession.getMapper(OaNoticeTemplateMapper.class);
        pushes = sqlSession.getMapper(OaScheduledPushMapper.class);
        pushRuns = sqlSession.getMapper(OaScheduledPushRunMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);
        bootstrapContext();

        outboxWriter = new NoticeOutboxWriter(new JdbcTemplate(dataSource));
        idempotencyGuard = new IdempotencyGuard(idempotencies);
        audienceResolver = new NoticeAudienceResolver(identity);
        announcementService = new AnnouncementServiceImpl(announcements, audience, reads, identity,
            preferences, outboxWriter, idempotencyGuard, audienceResolver);
        preferenceService = new PreferenceServiceImpl(preferences);
        outboxAdminService = new OutboxAdminServiceImpl(outbox);
        templateService = new NoticeTemplateServiceImpl(templates, audienceResolver, outboxWriter, preferences);
        scheduledPushService = new ScheduledPushServiceImpl(pushes, pushRuns, templates, announcements,
            announcementService, audienceResolver, outboxWriter, preferences, txTemplate);
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_announcement", "oa_announcement_audience", "oa_announcement_read",
            "oa_notification_preference", "oa_idempotency", "sys_outbox", "sys_user", "sys_dept",
            "sys_role", "sys_user_role", "oa_notice_template", "oa_scheduled_push", "oa_scheduled_push_run")) {
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

    public static void seedRole(long roleId, String roleKey, Long userId) {
        jdbcTemplate().update("INSERT INTO sys_role(role_id, role_name, role_key) VALUES(?,?,?)",
            roleId, roleKey, roleKey);
        if (userId != null) {
            jdbcTemplate().update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?,?)", userId, roleId);
        }
    }

    public static OaAnnouncement seedAnnouncement(long id, long publisherId, int status, int scopeType,
                                                  String scopeValues, String title) {
        OaAnnouncement announcement = new OaAnnouncement();
        announcement.setId(id);
        announcement.setTitle(title);
        announcement.setContent("公告正文");
        announcement.setNoticeType("company");
        announcement.setPublisherId(publisherId);
        announcement.setScopeType(scopeType);
        announcement.setScopeValues(scopeValues);
        announcement.setIsTop(0);
        announcement.setIsPopup(0);
        announcement.setStatus(status);
        announcement.setReadCount(0);
        announcement.setCreateTime(new Date());
        announcements.insert(announcement);
        return announcement;
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    public static OaNoticeTemplate seedTemplate(String code, String name, String titleTpl, String contentTpl,
                                                String varsJson, String msgType, int status) {
        OaNoticeTemplate template = new OaNoticeTemplate();
        template.setTemplateCode(code);
        template.setName(name);
        template.setTitleTpl(titleTpl);
        template.setContentTpl(contentTpl);
        template.setVarsJson(varsJson);
        template.setMsgType(msgType);
        template.setStatus(status);
        template.setCreateTime(java.time.LocalDateTime.now());
        templates.insert(template);
        return template;
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
                new ClassPathResource("nt-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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
