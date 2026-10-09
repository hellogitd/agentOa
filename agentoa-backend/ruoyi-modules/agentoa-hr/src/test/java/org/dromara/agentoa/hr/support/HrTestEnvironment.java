package org.dromara.agentoa.hr.support;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.github.linpeilie.Converter;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaEmployeeChange;
import org.dromara.agentoa.hr.domain.OaEmployeeEducation;
import org.dromara.agentoa.hr.domain.OaEmployeeHistory;
import org.dromara.agentoa.hr.domain.OaEmployeeWork;
import org.dromara.agentoa.hr.domain.OaContract;
import org.dromara.agentoa.hr.domain.OaIdempotency;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.agentoa.hr.domain.OaOffboarding;
import org.dromara.agentoa.hr.domain.OaOnboarding;
import org.dromara.agentoa.hr.mapper.OaContractMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeChangeMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeEducationMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeHistoryMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeWorkMapper;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.mapper.OaJobPositionMapper;
import org.dromara.agentoa.hr.mapper.OaOffboardingMapper;
import org.dromara.agentoa.hr.mapper.OaOnboardingMapper;
import org.dromara.agentoa.hr.service.IHrChangeService;
import org.dromara.agentoa.hr.service.IHrContractService;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.hr.service.IHrExperienceService;
import org.dromara.agentoa.hr.service.impl.HrChangeServiceImpl;
import org.dromara.agentoa.hr.service.impl.HrContractServiceImpl;
import org.dromara.agentoa.hr.service.impl.HrEmployeeServiceImpl;
import org.dromara.agentoa.hr.service.impl.HrExperienceServiceImpl;
import org.dromara.agentoa.hr.service.support.AccountFreezer;
import org.dromara.agentoa.hr.service.support.EmployeeNumberGenerator;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.bo.SysDeptBo;
import org.dromara.system.domain.vo.SysDeptVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysDeptService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * H2 + MyBatis-Plus 测试环境：真实 Mapper、真实服务实现、轻量 Converter 与登录上下文。
 */
public final class HrTestEnvironment {

    public static final Long USER_HR = 100L;
    public static final Long USER_EMPLOYEE = 200L;
    public static final Long USER_OTHER = 300L;
    public static final Long DEPT_A = 10L;
    public static final Long DEPT_B = 20L;

    private static DataSource dataSource;
    private static SqlSessionTemplate sqlSession;
    private static TransactionTemplate txTemplate;

    public static OaEmployeeMapper employees;
    public static OaEmployeeHistoryMapper histories;
    public static OaOnboardingMapper onboardings;
    public static OaOffboardingMapper offboardings;
    public static OaJobPositionMapper positions;
    public static OaIdempotencyMapper idempotencies;
    public static OaEmployeeEducationMapper educations;
    public static OaEmployeeWorkMapper works;
    public static OaEmployeeChangeMapper changes;
    public static OaContractMapper contracts;
    public static SysUserMapper users;
    public static IHrEmployeeService employeeService;
    public static IHrChangeService changeService;
    public static IHrContractService contractService;
    public static IHrExperienceService experienceService;

    private HrTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:hrtest-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
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
            for (Class<?> mapper : List.of(OaEmployeeMapper.class, OaEmployeeHistoryMapper.class,
                OaOnboardingMapper.class, OaOffboardingMapper.class, OaJobPositionMapper.class,
                OaIdempotencyMapper.class, OaEmployeeEducationMapper.class, OaEmployeeWorkMapper.class,
                OaEmployeeChangeMapper.class, OaContractMapper.class, SysUserMapper.class)) {
                configuration.addMapper(mapper);
            }
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        employees = sqlSession.getMapper(OaEmployeeMapper.class);
        histories = sqlSession.getMapper(OaEmployeeHistoryMapper.class);
        onboardings = sqlSession.getMapper(OaOnboardingMapper.class);
        offboardings = sqlSession.getMapper(OaOffboardingMapper.class);
        positions = sqlSession.getMapper(OaJobPositionMapper.class);
        idempotencies = sqlSession.getMapper(OaIdempotencyMapper.class);
        educations = sqlSession.getMapper(OaEmployeeEducationMapper.class);
        works = sqlSession.getMapper(OaEmployeeWorkMapper.class);
        changes = sqlSession.getMapper(OaEmployeeChangeMapper.class);
        contracts = sqlSession.getMapper(OaContractMapper.class);
        users = sqlSession.getMapper(SysUserMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        bootstrapConverter();
        employeeService = new HrEmployeeServiceImpl(employees, histories, onboardings, offboardings,
            positions, deptService(), new EmployeeNumberGenerator(employees), new AccountFreezer(users));
        changeService = new HrChangeServiceImpl(changes, employees, histories, positions);
        contractService = new HrContractServiceImpl(contracts, employees);
        experienceService = new HrExperienceServiceImpl(educations, works, employees);
    }

    /** 事务包装：与生产 @Transactional 语义一致，验证回滚。 */
    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        jdbcTemplate().execute("DELETE FROM oa_employee");
        jdbcTemplate().execute("DELETE FROM oa_employee_history");
        jdbcTemplate().execute("DELETE FROM oa_onboarding");
        jdbcTemplate().execute("DELETE FROM oa_offboarding");
        jdbcTemplate().execute("DELETE FROM oa_job_position");
        jdbcTemplate().execute("DELETE FROM oa_idempotency");
        jdbcTemplate().execute("DELETE FROM oa_employee_education");
        jdbcTemplate().execute("DELETE FROM oa_employee_work");
        jdbcTemplate().execute("DELETE FROM oa_employee_change");
        jdbcTemplate().execute("DELETE FROM oa_contract");
        jdbcTemplate().execute("DELETE FROM sys_user");
    }

    /** 以指定账号与权限登录（menuPermission 决定 HR/敏感授权判定）。 */
    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission) {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setNickname("u" + userId);
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

    private static org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("hr-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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

    public static ISysDeptService deptService() {
        return new ISysDeptService() {
            private final Map<Long, SysDeptVo> depts = new LinkedHashMap<>();

            {
                depts.put(DEPT_A, dept(DEPT_A, "管理部", 0L));
                depts.put(DEPT_B, dept(DEPT_B, "研发部", DEPT_A));
            }

            private SysDeptVo dept(Long id, String name, Long parentId) {
                SysDeptVo vo = new SysDeptVo();
                vo.setDeptId(id);
                vo.setDeptName(name);
                vo.setParentId(parentId);
                vo.setAncestors(parentId == 0L ? "0" : "0," + parentId);
                vo.setStatus("0");
                return vo;
            }

            @Override
            public org.dromara.common.mybatis.core.page.TableDataInfo<SysDeptVo> selectPageDeptList(
                SysDeptBo dept, org.dromara.common.mybatis.core.page.PageQuery pageQuery) {
                return null;
            }

            @Override
            public List<SysDeptVo> selectDeptList(SysDeptBo dept) {
                return new ArrayList<>(depts.values());
            }

            @Override
            public List<cn.hutool.core.lang.tree.Tree<Long>> selectDeptTreeList(SysDeptBo dept) {
                return List.of();
            }

            @Override
            public List<cn.hutool.core.lang.tree.Tree<Long>> buildDeptTreeSelect(List<SysDeptVo> deptList) {
                return List.of();
            }

            @Override
            public List<Long> selectDeptListByRoleId(Long roleId) {
                return List.of();
            }

            @Override
            public SysDeptVo selectDeptById(Long deptId) {
                return depts.get(deptId);
            }

            @Override
            public List<SysDeptVo> selectDeptByIds(List<Long> deptIds) {
                return deptIds.stream().map(depts::get).filter(java.util.Objects::nonNull).toList();
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
            public boolean checkDeptNameUnique(SysDeptBo dept) {
                return true;
            }

            @Override
            public void checkDeptDataScope(Long deptId) {
            }

            @Override
            public int insertDept(SysDeptBo bo) {
                return 1;
            }

            @Override
            public int updateDept(SysDeptBo bo) {
                return 1;
            }

            @Override
            public int deleteDeptById(Long deptId) {
                return 1;
            }
        };
    }

    /** 反射式 Bean 复制转换器，替代 MapStruct 生成器（测试专用）。 */
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

    /** 便于断言的实体快照。 */
    public static OaEmployee employee(Long id) {
        return employees.selectById(id);
    }

    public static long countEmployees() {
        return employees.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }

    public static long countOnboardings() {
        return onboardings.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }

    public static long countOffboardings() {
        return offboardings.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }

    public static long countIdempotencies() {
        return idempotencies.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }

    public static List<OaEmployeeHistory> historyOf(Long employeeId) {
        return histories.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OaEmployeeHistory>()
            .eq(OaEmployeeHistory::getEmployeeId, employeeId));
    }
}
