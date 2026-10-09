package org.dromara.agentoa.workflow.support;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.github.linpeilie.Converter;
import org.dromara.agentoa.hr.mapper.OaEmployeeHistoryMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.mapper.OaJobPositionMapper;
import org.dromara.agentoa.hr.mapper.OaOffboardingMapper;
import org.dromara.agentoa.hr.mapper.OaOnboardingMapper;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.hr.service.impl.HrEmployeeServiceImpl;
import org.dromara.agentoa.hr.service.support.AccountFreezer;
import org.dromara.agentoa.hr.service.support.EmployeeNumberGenerator;
import org.dromara.agentoa.workflow.assigner.AssigneeResolver;
import org.dromara.agentoa.workflow.mapper.OaCorrectionRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowBusinessRefMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowCategoryMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowCcMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDelegateMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowGenericRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowIdempotencyMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowTaskActionMapper;
import org.dromara.agentoa.workflow.mapper.OaLeaveRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaLifecycleRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaOvertimeRequestMapper;
import org.dromara.agentoa.workflow.mapper.OaReimburseRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IWorkflowDelegateService;
import org.dromara.agentoa.workflow.service.IWorkflowInstanceService;
import org.dromara.agentoa.workflow.service.IWorkflowTaskService;
import org.dromara.agentoa.workflow.service.IWorkflowTemplateService;
import org.dromara.agentoa.workflow.service.impl.CorrectionRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.CountersignFlowHandler;
import org.dromara.agentoa.workflow.service.impl.EitherSignFlowHandler;
import org.dromara.agentoa.workflow.service.impl.GenericRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.LeaveRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.LifecycleRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.OffboardFlowHandler;
import org.dromara.agentoa.workflow.service.impl.OvertimeRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.RegularizeFlowHandler;
import org.dromara.agentoa.workflow.service.impl.ReimburseRequestServiceImpl;
import org.dromara.agentoa.workflow.service.impl.WorkflowDelegateServiceImpl;
import org.dromara.agentoa.workflow.service.impl.WorkflowInstanceServiceImpl;
import org.dromara.agentoa.workflow.service.impl.WorkflowTaskServiceImpl;
import org.dromara.agentoa.workflow.service.impl.WorkflowTemplateServiceImpl;
import org.dromara.agentoa.workflow.service.support.CcMaterializer;
import org.dromara.agentoa.workflow.service.support.DelegationResolver;import org.dromara.agentoa.workflow.service.support.DurationCalculators;
import org.dromara.agentoa.workflow.service.support.FlowEventListenerRegistry;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard;
import org.dromara.agentoa.workflow.service.support.MiCollectionResolver;
import org.dromara.agentoa.workflow.service.support.OutboxWriter;
import org.dromara.agentoa.workflow.service.support.TemplateRegistrar;
import org.dromara.agentoa.workflow.service.support.UserSelectResolver;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.mapper.SysUserMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcConnectionPool;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.support.GenericApplicationContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * H2 + MyBatis-Plus + Flowable 组合测试环境：真实 Mapper、真实服务、真实流程引擎、共享事务管理器。
 */
public final class WfTestEnvironment {

    public static final Long USER_LEADER = 100L;
    public static final Long USER_EMPLOYEE = 200L;
    public static final Long USER_OTHER = 300L;
    public static final Long USER_HR = 400L;
    public static final Long USER_FINANCE = 500L;
    public static final Long USER_CASHIER = 600L;
    public static final Long USER_DIRECTOR = 700L;
    /** 行政（ROLE:admin）与总经理（ROLE:gm），M4 内置模板审批链依赖 */
    public static final Long USER_ADMIN = 800L;
    public static final Long USER_GM = 900L;
    public static final Long DEPT_A = 10L;

    private static DataSource dataSource;
    private static SqlSessionTemplate sqlSession;
    private static TransactionTemplate txTemplate;
    private static ProcessEngine engine;

    public static OaFlowCategoryMapper categories;
    public static OaFlowDefinitionMapper definitions;
    public static OaFlowDefinitionVersionMapper versions;
    public static OaFlowFormVersionMapper forms;
    public static OaFlowInstanceMapper instances;
    public static OaFlowTaskActionMapper actions;
    public static OaFlowCcMapper flowCcs;
    public static OaFlowDelegateMapper flowDelegates;
    public static OaFlowBusinessRefMapper refs;
    public static OaFlowIdempotencyMapper flowIdempotencies;
    public static OaLeaveRequestMapper leaves;
    public static OaOvertimeRequestMapper overtimes;
    public static OaCorrectionRequestMapper corrections;
    public static OaReimburseRequestMapper reimburses;
    public static OaLifecycleRequestMapper lifecycles;
    public static WorkflowIdentityReadMapper identity;
    public static SysUserMapper users;
    public static OaEmployeeMapper employees;
    public static OaEmployeeHistoryMapper histories;

    public static FlowHandlerRegistry handlerRegistry;
    public static FlowEventListenerRegistry eventListenerRegistry;
    public static DurationCalculators durationCalculators;
    public static OutboxWriter outboxWriter;
    public static WorkflowOrchestrator orchestrator;
    public static TemplateRegistrar templateRegistrar;
    public static IWorkflowTemplateService templateService;
    public static IWorkflowInstanceService instanceService;
    public static IWorkflowTaskService taskService;
    public static IWorkflowDelegateService delegateService;
    public static DelegationResolver delegationResolver;
    public static FlowIdempotencyGuard flowIdempotencyGuard;
    public static LeaveRequestServiceImpl leaveService;
    public static OvertimeRequestServiceImpl overtimeService;
    public static CorrectionRequestServiceImpl correctionService;
    public static ReimburseRequestServiceImpl reimburseService;
    public static LifecycleRequestServiceImpl lifecycleService;
    public static GenericRequestServiceImpl genericService;
    public static IHrEmployeeService hrEmployeeService;

    private WfTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:wftest-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
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
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor =
                new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(
                com.baomidou.mybatisplus.annotation.DbType.H2));
            configuration.addInterceptor(interceptor);
            for (Class<?> mapper : List.of(
                OaFlowCategoryMapper.class, OaFlowDefinitionMapper.class, OaFlowDefinitionVersionMapper.class,
                OaFlowFormVersionMapper.class, OaFlowInstanceMapper.class, OaFlowTaskActionMapper.class,
                OaFlowBusinessRefMapper.class, OaFlowIdempotencyMapper.class, OaFlowCcMapper.class,
                OaFlowDelegateMapper.class, OaFlowGenericRequestMapper.class,
                OaLeaveRequestMapper.class, OaOvertimeRequestMapper.class, OaCorrectionRequestMapper.class,
                OaReimburseRequestMapper.class, OaLifecycleRequestMapper.class,
                WorkflowIdentityReadMapper.class, SysUserMapper.class,
                OaEmployeeMapper.class, OaEmployeeHistoryMapper.class, OaOnboardingMapper.class,
                OaOffboardingMapper.class, OaJobPositionMapper.class, OaIdempotencyMapper.class)) {
                configuration.addMapper(mapper);
            }
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        categories = sqlSession.getMapper(OaFlowCategoryMapper.class);
        definitions = sqlSession.getMapper(OaFlowDefinitionMapper.class);
        versions = sqlSession.getMapper(OaFlowDefinitionVersionMapper.class);
        forms = sqlSession.getMapper(OaFlowFormVersionMapper.class);
        instances = sqlSession.getMapper(OaFlowInstanceMapper.class);
        actions = sqlSession.getMapper(OaFlowTaskActionMapper.class);
        flowCcs = sqlSession.getMapper(OaFlowCcMapper.class);
        flowDelegates = sqlSession.getMapper(OaFlowDelegateMapper.class);
        refs = sqlSession.getMapper(OaFlowBusinessRefMapper.class);
        flowIdempotencies = sqlSession.getMapper(OaFlowIdempotencyMapper.class);
        leaves = sqlSession.getMapper(OaLeaveRequestMapper.class);
        overtimes = sqlSession.getMapper(OaOvertimeRequestMapper.class);
        corrections = sqlSession.getMapper(OaCorrectionRequestMapper.class);
        reimburses = sqlSession.getMapper(OaReimburseRequestMapper.class);
        lifecycles = sqlSession.getMapper(OaLifecycleRequestMapper.class);
        identity = sqlSession.getMapper(WorkflowIdentityReadMapper.class);
        users = sqlSession.getMapper(SysUserMapper.class);
        employees = sqlSession.getMapper(OaEmployeeMapper.class);
        histories = sqlSession.getMapper(OaEmployeeHistoryMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        var engineConfig = new SpringProcessEngineConfiguration();
        engineConfig.setDataSource(dataSource);
        engineConfig.setTransactionManager(txManager);
        engineConfig.setDatabaseSchemaUpdate("true");
        engineConfig.setAsyncExecutorActivate(false);
        engine = engineConfig.buildProcessEngine();

        bootstrapContext();

        handlerRegistry = new FlowHandlerRegistry();
        eventListenerRegistry = new FlowEventListenerRegistry();
        durationCalculators = new DurationCalculators();
        outboxWriter = new OutboxWriter(new JdbcTemplate(dataSource));
        orchestrator = new WorkflowOrchestrator(handlerRegistry, eventListenerRegistry, definitions, versions, forms, instances,
            refs, identity, outboxWriter, engine.getRuntimeService(), engine.getTaskService(),
            new MiCollectionResolver(new AssigneeResolver(identity)),
            new CcMaterializer(flowCcs, new AssigneeResolver(identity)),
            new UserSelectResolver(identity));
        templateRegistrar = new TemplateRegistrar(definitions, versions, forms, engine.getRepositoryService());
        templateService = new WorkflowTemplateServiceImpl(categories, definitions, versions, forms, instances,
            engine.getRepositoryService(), identity);
        delegationResolver = new DelegationResolver(flowDelegates);
        instanceService = new WorkflowInstanceServiceImpl(instances, actions, definitions, versions, forms,
            orchestrator, outboxWriter, engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService(),
            engine.getRepositoryService());
        taskService = new WorkflowTaskServiceImpl(instances, actions, flowCcs, identity, orchestrator, outboxWriter,
            delegationResolver, engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService());
        delegateService = new WorkflowDelegateServiceImpl(flowDelegates, identity);
        flowIdempotencyGuard = new FlowIdempotencyGuard(flowIdempotencies);

        hrEmployeeService = new HrEmployeeServiceImpl(
            sqlSession.getMapper(OaEmployeeMapper.class),
            sqlSession.getMapper(OaEmployeeHistoryMapper.class),
            sqlSession.getMapper(OaOnboardingMapper.class),
            sqlSession.getMapper(OaOffboardingMapper.class),
            sqlSession.getMapper(OaJobPositionMapper.class),
            deptService(), new EmployeeNumberGenerator(sqlSession.getMapper(OaEmployeeMapper.class)),
            new AccountFreezer(users));

        lifecycleService = new LifecycleRequestServiceImpl(lifecycles, identity, orchestrator, hrEmployeeService);
        genericService = new GenericRequestServiceImpl(
            sqlSession.getMapper(OaFlowGenericRequestMapper.class), definitions, identity, orchestrator, handlerRegistry);
        genericService.registerHandler();
        leaveService = new LeaveRequestServiceImpl(leaves, identity, orchestrator, handlerRegistry, durationCalculators);
        leaveService.registerHandler();
        overtimeService = new OvertimeRequestServiceImpl(overtimes, identity, orchestrator, handlerRegistry);
        overtimeService.registerHandler();
        correctionService = new CorrectionRequestServiceImpl(corrections, identity, orchestrator, handlerRegistry);
        correctionService.registerHandler();
        reimburseService = new ReimburseRequestServiceImpl(reimburses, identity, orchestrator, handlerRegistry);
        reimburseService.registerHandler();
        new RegularizeFlowHandler(lifecycleService, handlerRegistry).registerHandler();
        new OffboardFlowHandler(lifecycleService, handlerRegistry).registerHandler();
        new CountersignFlowHandler(handlerRegistry).registerHandler();
        new EitherSignFlowHandler(handlerRegistry).registerHandler();
    }

    public static ProcessEngine engine() {
        return engine;
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void runInTransaction(Runnable action) {
        txTemplate.executeWithoutResult(status -> action.run());
    }

    public static JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    public static void registerTemplates() {
        templateRegistrar.registerAll();
    }

    public static void clearFlowData() {
        jdbc().execute("DELETE FROM oa_leave_request");
        jdbc().execute("DELETE FROM oa_overtime_request");
        jdbc().execute("DELETE FROM oa_correction_request");
        jdbc().execute("DELETE FROM oa_reimburse_request");
        jdbc().execute("DELETE FROM oa_lifecycle_request");
        jdbc().execute("DELETE FROM oa_flow_task_action");
        jdbc().execute("DELETE FROM oa_flow_business_ref");
        jdbc().execute("DELETE FROM oa_flow_cc");
        jdbc().execute("DELETE FROM oa_flow_delegate");
        jdbc().execute("DELETE FROM oa_flow_generic_request");
        jdbc().execute("DELETE FROM oa_flow_instance");
        jdbc().execute("DELETE FROM oa_flow_idempotency");
        jdbc().execute("DELETE FROM sys_outbox");
    }

    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission) {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setNickname("u" + userId);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setTenantId("000000");
        user.setDeptId(deptId);
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
        }
    }

    public static void createSysUser(Long userId, Long deptId, String nick) {
        jdbc().update("INSERT INTO sys_user(user_id, user_name, nick_name, dept_id, status, del_flag) VALUES(?,?,?,?,'0','0')",
            userId, "u" + userId, nick, deptId);
    }

    public static void grantRole(Long userId, long roleId) {
        jdbc().update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?,?)", userId, roleId);
    }

    public static long countInstances() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM oa_flow_instance", Long.class);
    }

    private static void bootstrapContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("objectMapper", com.fasterxml.jackson.databind.ObjectMapper.class,
            () -> new com.fasterxml.jackson.databind.ObjectMapper());
        context.registerBean(Converter.class, ReflectiveConverter::new);
        context.registerBean(AssigneeResolver.class, () -> new AssigneeResolver(identity));
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("wf-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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

    private static org.dromara.system.service.ISysDeptService deptService() {
        return new org.dromara.system.service.ISysDeptService() {
            @Override
            public org.dromara.common.mybatis.core.page.TableDataInfo<org.dromara.system.domain.vo.SysDeptVo> selectPageDeptList(
                org.dromara.system.domain.bo.SysDeptBo dept,
                org.dromara.common.mybatis.core.page.PageQuery pageQuery) {
                return null;
            }

            @Override
            public List<org.dromara.system.domain.vo.SysDeptVo> selectDeptList(org.dromara.system.domain.bo.SysDeptBo dept) {
                return List.of();
            }

            @Override
            public List<cn.hutool.core.lang.tree.Tree<Long>> selectDeptTreeList(org.dromara.system.domain.bo.SysDeptBo dept) {
                return List.of();
            }

            @Override
            public List<cn.hutool.core.lang.tree.Tree<Long>> buildDeptTreeSelect(List<org.dromara.system.domain.vo.SysDeptVo> deptList) {
                return List.of();
            }

            @Override
            public List<Long> selectDeptListByRoleId(Long roleId) {
                return List.of();
            }

            @Override
            public org.dromara.system.domain.vo.SysDeptVo selectDeptById(Long deptId) {
                return null;
            }

            @Override
            public List<org.dromara.system.domain.vo.SysDeptVo> selectDeptByIds(List<Long> deptIds) {
                return List.of();
            }

            @Override
            public long selectNormalChildrenDeptById(Long deptId) {
                return 0;
            }

            @Override
            public boolean hasChildByDeptId(Long deptId) {
                return false;
            }

            @Override
            public boolean checkDeptExistUser(Long deptId) {
                return false;
            }

            @Override
            public boolean checkDeptNameUnique(org.dromara.system.domain.bo.SysDeptBo bo) {
                return true;
            }

            @Override
            public void checkDeptDataScope(Long deptId) {
            }

            @Override
            public int insertDept(org.dromara.system.domain.bo.SysDeptBo bo) {
                return 1;
            }

            @Override
            public int updateDept(org.dromara.system.domain.bo.SysDeptBo bo) {
                return 1;
            }

            @Override
            public int deleteDeptById(Long deptId) {
                return 1;
            }
        };
    }

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
