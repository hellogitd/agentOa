package org.dromara.agentoa.finance.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.finance.domain.OaExpenseType;
import org.dromara.agentoa.finance.domain.OaInvoice;
import org.dromara.agentoa.finance.mapper.FinanceIdentityReadMapper;
import org.dromara.agentoa.finance.mapper.FinanceReportMapper;
import org.dromara.agentoa.finance.mapper.OaExpenseItemMapper;
import org.dromara.agentoa.finance.mapper.OaExpenseTypeMapper;
import org.dromara.agentoa.finance.mapper.OaFinanceEventMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceAllocationMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceReservationMapper;
import org.dromara.agentoa.finance.mapper.OaPaymentRecordMapper;
import org.dromara.agentoa.finance.mapper.OaBudgetMapper;
import org.dromara.agentoa.finance.mapper.OaBudgetLedgerMapper;
import org.dromara.agentoa.finance.service.impl.BudgetServiceImpl;
import org.dromara.agentoa.finance.service.impl.ExpenseTypeServiceImpl;
import org.dromara.agentoa.finance.service.impl.FinanceReportServiceImpl;
import org.dromara.agentoa.finance.service.impl.InvoiceServiceImpl;
import org.dromara.agentoa.finance.service.impl.PaymentServiceImpl;
import org.dromara.agentoa.finance.service.support.FinanceFlowListener;
import org.dromara.agentoa.finance.service.support.FinanceLedgerWriter;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.workflow.domain.OaReimburseRequest;
import org.dromara.agentoa.workflow.mapper.OaReimburseRequestMapper;
import org.dromara.agentoa.workflow.service.support.FlowEventListenerRegistry;
import org.dromara.agentoa.workflow.service.support.OutboxWriter;
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
public final class FinanceTestEnvironment {

    public static final Long USER_EMPLOYEE = 200L;
    public static final Long USER_OTHER = 300L;
    public static final Long USER_FINANCE = 500L;
    public static final Long USER_CASHIER = 600L;
    public static final Long DEPT_A = 10L;

    private static DataSource dataSource;
    private static TransactionTemplate txTemplate;

    public static OaExpenseTypeMapper expenseTypes;
    public static OaInvoiceMapper invoices;
    public static OaInvoiceReservationMapper reservations;
    public static OaInvoiceAllocationMapper allocations;
    public static OaExpenseItemMapper expenseItems;
    public static OaPaymentRecordMapper payments;
    public static OaFinanceEventMapper financeEvents;
    public static FinanceIdentityReadMapper identity;
    public static FinanceReportMapper reports;
    public static OaReimburseRequestMapper claims;
    public static OaIdempotencyMapper idempotencies;
    public static OaBudgetMapper budgets;
    public static OaBudgetLedgerMapper budgetLedgers;

    public static FinanceLedgerWriter ledgerWriter;
    public static InvoiceServiceImpl invoiceService;
    public static ExpenseTypeServiceImpl expenseTypeService;
    public static PaymentServiceImpl paymentService;
    public static FinanceReportServiceImpl reportService;
    public static FinanceFlowListener flowListener;
    public static BudgetServiceImpl budgetService;
    public static FlowEventListenerRegistry listenerRegistry;
    public static IdempotencyGuard idempotencyGuard;

    private FinanceTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:fintest-" + System.nanoTime()
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
            for (Class<?> mapper : List.of(OaExpenseTypeMapper.class, OaInvoiceMapper.class,
                OaInvoiceReservationMapper.class, OaInvoiceAllocationMapper.class, OaExpenseItemMapper.class, OaPaymentRecordMapper.class,
                OaFinanceEventMapper.class, OaReimburseRequestMapper.class, OaIdempotencyMapper.class,
                OaBudgetMapper.class, OaBudgetLedgerMapper.class)) {
                configuration.addMapper(mapper);
            }
            configuration.addMapper(FinanceIdentityReadMapper.class);
            configuration.addMapper(FinanceReportMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        expenseTypes = sqlSession.getMapper(OaExpenseTypeMapper.class);
        invoices = sqlSession.getMapper(OaInvoiceMapper.class);
        reservations = sqlSession.getMapper(OaInvoiceReservationMapper.class);
        allocations = sqlSession.getMapper(OaInvoiceAllocationMapper.class);
        expenseItems = sqlSession.getMapper(OaExpenseItemMapper.class);
        payments = sqlSession.getMapper(OaPaymentRecordMapper.class);
        financeEvents = sqlSession.getMapper(OaFinanceEventMapper.class);
        claims = sqlSession.getMapper(OaReimburseRequestMapper.class);
        idempotencies = sqlSession.getMapper(OaIdempotencyMapper.class);
        budgets = sqlSession.getMapper(OaBudgetMapper.class);
        budgetLedgers = sqlSession.getMapper(OaBudgetLedgerMapper.class);
        identity = sqlSession.getMapper(FinanceIdentityReadMapper.class);
        reports = sqlSession.getMapper(FinanceReportMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        bootstrapContext();

        ledgerWriter = new FinanceLedgerWriter(reservations, allocations, financeEvents);
        invoiceService = new InvoiceServiceImpl(invoices, allocations, ledgerWriter, identity);
        expenseTypeService = new ExpenseTypeServiceImpl(expenseTypes, expenseItems);
        idempotencyGuard = new IdempotencyGuard(idempotencies);
        OutboxWriter outboxWriter = new OutboxWriter(new JdbcTemplate(dataSource));
        paymentService = new PaymentServiceImpl(payments, claims, invoiceService, ledgerWriter,
            identity, idempotencyGuard, outboxWriter);
        reportService = new FinanceReportServiceImpl(reports, identity);
        budgetService = new BudgetServiceImpl(budgets, budgetLedgers);
        listenerRegistry = new FlowEventListenerRegistry();
        flowListener = new FinanceFlowListener(listenerRegistry, claims, expenseItems, invoices,
            invoiceService, ledgerWriter, identity, budgetService);
        flowListener.register();
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_expense_type", "oa_invoice", "oa_invoice_reservation",
            "oa_expense_item", "oa_payment_record", "oa_finance_event", "oa_reimburse_request", "oa_invoice_allocation",
            "oa_idempotency", "oa_budget", "oa_budget_ledger", "sys_outbox", "sys_file", "sys_user", "sys_dept")) {
            jdbcTemplate().execute("DELETE FROM " + table);
        }
    }

    public static void loginAs(Long userId, Long deptId, Set<String> menuPermission, Set<String> rolePermission) {
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
        user.setRolePermission(rolePermission == null ? Set.of() : rolePermission);
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

    public static OaExpenseType seedExpenseType(long id, String code, String name) {
        OaExpenseType type = new OaExpenseType();
        type.setId(id);
        type.setParentId(0L);
        type.setName(name);
        type.setCode(code);
        type.setSort(0);
        type.setBudgetControl(0);
        type.setStatus("0");
        type.setCreateTime(new Date());
        expenseTypes.insert(type);
        return type;
    }

    public static OaInvoice seedInvoice(long id, long ownerUserId, String invoiceNo, String amount,
                                        Long occupiedReimburseId, Long paidReimburseId) {
        OaInvoice invoice = new OaInvoice();
        invoice.setId(id);
        invoice.setInvoiceType("VAT_NORMAL");
        invoice.setInvoiceCode("");
        invoice.setInvoiceNo(invoiceNo);
        invoice.setAmount(new java.math.BigDecimal(amount));
        invoice.setFingerprint("seed-" + id);
        invoice.setOwnerUserId(ownerUserId);
        invoice.setOccupiedReimburseId(occupiedReimburseId);
        invoice.setPaidReimburseId(paidReimburseId);
        invoice.setLockVersion(0);
        invoice.setCreateTime(new Date());
        invoices.insert(invoice);
        return invoice;
    }

    public static OaReimburseRequest seedClaim(long id, long userId, String amount, String detailsJson, int status) {
        OaReimburseRequest claim = new OaReimburseRequest();
        claim.setId(id);
        claim.setReimburseNo("RB" + id);
        claim.setUserId(userId);
        claim.setEmployeeId(userId);
        claim.setReimburseType("expense");
        claim.setTotalAmount(new java.math.BigDecimal(amount));
        claim.setCurrency("CNY");
        claim.setDetailsJson(detailsJson);
        claim.setStatus(status);
        claim.setSubmissionNo(1);
        claim.setLockVersion(0);
        claim.setCreateTime(new Date());
        claims.insert(claim);
        return claim;
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
                new ClassPathResource("fin-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
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
