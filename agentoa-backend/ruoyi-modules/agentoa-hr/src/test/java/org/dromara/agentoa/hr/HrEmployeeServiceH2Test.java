package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeBo;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeProfileBo;
import org.dromara.agentoa.hr.domain.bo.OaOffboardBo;
import org.dromara.agentoa.hr.domain.bo.OaOnboardBo;
import org.dromara.agentoa.hr.domain.bo.OaRegularizeBo;
import org.dromara.agentoa.hr.domain.bo.OaStatusBo;
import org.dromara.agentoa.hr.domain.enums.EmployeeStatus;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;
import org.dromara.agentoa.hr.service.impl.HrEmployeeServiceImpl;
import org.dromara.agentoa.hr.service.support.AccountFreezer;
import org.dromara.agentoa.hr.service.support.EmployeeNumberGenerator;
import org.dromara.agentoa.hr.support.HrTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 集成测试：生命周期幂等、并发工号重试、越权、事务回滚、导入报告。
 */
class HrEmployeeServiceH2Test {

    private static final Set<String> HR_PERMS = Set.of("hr:employee:edit", "hr:employee:sensitive");
    private static final Set<String> PLAIN_PERMS = Set.of("hr:employee:list");

    @BeforeAll
    static void boot() {
        HrTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        HrTestEnvironment.clearData();
        HrTestEnvironment.logout();
    }

    private OaOnboardBo onboardBo(String name, Long deptId) {
        OaOnboardBo bo = new OaOnboardBo();
        bo.setName(name);
        bo.setDeptId(deptId);
        bo.setEntryDate(LocalDate.of(2026, 10, 1));
        return bo;
    }

    @Test
    void createGeneratesEmployeeNumberAndHistory() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        OaEmployeeVo vo = HrTestEnvironment.employeeService.createEmployee(bo);

        assertThat(vo.getEmployeeNo()).startsWith("E").hasSize(13);
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        assertThat(HrTestEnvironment.historyOf(vo.getId()))
            .extracting(h -> h.getEventType())
            .containsExactly("CREATE");
    }

    @Test
    void duplicateExplicitNumberRejected() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeBo first = new OaEmployeeBo();
        first.setName("张三");
        first.setEmployeeNo("E100");
        first.setDeptId(HrTestEnvironment.DEPT_A);
        HrTestEnvironment.employeeService.createEmployee(first);

        OaEmployeeBo second = new OaEmployeeBo();
        second.setName("李四");
        second.setEmployeeNo("E100");
        second.setDeptId(HrTestEnvironment.DEPT_A);
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.createEmployee(second))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已存在");
    }

    @Test
    void generatedNumberRetriesOnConflict() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployee existing = new OaEmployee();
        existing.setName("占位");
        existing.setEmployeeNo("E202610020001");
        existing.setDeptId(HrTestEnvironment.DEPT_A);
        existing.setStatus("DRAFT");
        HrTestEnvironment.employees.insert(existing);

        EmployeeNumberGenerator colliding = new EmployeeNumberGenerator(HrTestEnvironment.employees) {
            private int calls;

            @Override
            public String next() {
                calls++;
                return calls == 1 ? "E202610020001" : super.next();
            }
        };
        HrEmployeeServiceImpl service = new HrEmployeeServiceImpl(HrTestEnvironment.employees,
            HrTestEnvironment.histories, HrTestEnvironment.onboardings, HrTestEnvironment.offboardings,
            HrTestEnvironment.positions, HrTestEnvironment.deptService(), colliding,
            new AccountFreezer(HrTestEnvironment.users));

        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        OaEmployeeVo vo = service.createEmployee(bo);
        assertThat(vo.getEmployeeNo()).isNotEqualTo("E202610020001");
        assertThat(HrTestEnvironment.countEmployees()).isEqualTo(2);
    }

    @Test
    void onboardingEventIsSinglePerEmployee() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        OaEmployeeVo vo = HrTestEnvironment.employeeService.createEmployee(bo);

        OaStatusBo toProbation = new OaStatusBo();
        toProbation.setStatus("PROBATION");
        OaEmployeeVo probation = HrTestEnvironment.employeeService.updateStatus(vo.getId(), toProbation);
        assertThat(probation.getStatus()).isEqualTo("PROBATION");
        assertThat(probation.getEntryDate()).isEqualTo(LocalDate.now());

        HrTestEnvironment.employeeService.updateStatus(vo.getId(), toProbation);
        assertThat(HrTestEnvironment.countOnboardings()).isEqualTo(1);
        assertThat(HrTestEnvironment.historyOf(vo.getId()))
            .filteredOn(h -> "ONBOARDING".equals(h.getEventType()))
            .hasSize(1);
    }

    @Test
    void regularizeIsIdempotent() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeVo vo = HrTestEnvironment.employeeService.onboard(onboardBo("张三", HrTestEnvironment.DEPT_A));
        OaRegularizeBo cmd = new OaRegularizeBo();
        cmd.setEmployeeId(vo.getId());

        OaEmployeeVo active = HrTestEnvironment.employeeService.regularize(cmd);
        assertThat(active.getStatus()).isEqualTo("ACTIVE");
        assertThat(active.getRegularDate()).isEqualTo(LocalDate.now());

        HrTestEnvironment.employeeService.regularize(cmd);
        assertThat(HrTestEnvironment.historyOf(vo.getId()))
            .filteredOn(h -> "PROBATION".equals(h.getEventType()))
            .hasSize(1);
    }

    @Test
    void offboardFreezesAccountAndIsIdempotent() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        SysUser account = new SysUser();
        account.setUserId(900L);
        account.setUserName("zhangsan");
        account.setNickName("张三");
        account.setStatus("0");
        HrTestEnvironment.users.insert(account);

        OaOnboardBo bo = onboardBo("张三", HrTestEnvironment.DEPT_A);
        OaEmployeeVo vo = HrTestEnvironment.employeeService.onboard(bo);
        OaEmployee employee = HrTestEnvironment.employee(vo.getId());
        employee.setUserId(900L);
        HrTestEnvironment.employees.updateById(employee);

        OaOffboardBo cmd = new OaOffboardBo();
        cmd.setEmployeeId(vo.getId());
        cmd.setReason("个人原因");
        cmd.setLastWorkingDay(LocalDate.of(2026, 12, 31));
        OaEmployeeVo left = HrTestEnvironment.employeeService.offboard(cmd);

        assertThat(left.getStatus()).isEqualTo("LEFT");
        assertThat(left.getLeaveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(HrTestEnvironment.users.selectById(900L).getStatus()).isEqualTo("1");
        assertThat(HrTestEnvironment.countOffboardings()).isEqualTo(1);

        HrTestEnvironment.employeeService.offboard(cmd);
        assertThat(HrTestEnvironment.countOffboardings()).isEqualTo(1);
        assertThat(HrTestEnvironment.historyOf(vo.getId()))
            .filteredOn(h -> "OFFBOARDING".equals(h.getEventType()))
            .hasSize(1);
    }

    @Test
    void illegalTransitionsRejected() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeVo draft = HrTestEnvironment.employeeService.onboard(onboardBo("张三", HrTestEnvironment.DEPT_A));
        OaEmployee employee = HrTestEnvironment.employee(draft.getId());
        employee.setStatus("DRAFT");
        HrTestEnvironment.employees.updateById(employee);

        OaRegularizeBo regularize = new OaRegularizeBo();
        regularize.setEmployeeId(draft.getId());
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.regularize(regularize))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能办理");

        OaStatusBo backToDraft = new OaStatusBo();
        backToDraft.setStatus("DRAFT");
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.updateStatus(draft.getId(), backToDraft))
            .isInstanceOf(ServiceException.class);

        OaStatusBo disable = new OaStatusBo();
        disable.setStatus("DISABLED");
        HrTestEnvironment.employeeService.updateStatus(draft.getId(), disable);
        assertThat(HrTestEnvironment.employee(draft.getId()).getStatus()).isEqualTo("DISABLED");

        OaOffboardBo offboard = new OaOffboardBo();
        offboard.setEmployeeId(draft.getId());
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.offboard(offboard))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void employeeCannotReadOthersAndManagerReadsOwnDepartment() {
        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        bo.setUserId(HrTestEnvironment.USER_EMPLOYEE);

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeVo mine = HrTestEnvironment.employeeService.createEmployee(bo);

        OaEmployeeBo otherBo = new OaEmployeeBo();
        otherBo.setName("李四");
        otherBo.setDeptId(HrTestEnvironment.DEPT_B);
        otherBo.setUserId(HrTestEnvironment.USER_OTHER);
        OaEmployeeVo other = HrTestEnvironment.employeeService.createEmployee(otherBo);

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_EMPLOYEE, HrTestEnvironment.DEPT_A, PLAIN_PERMS);
        assertThat(HrTestEnvironment.employeeService.selectEmployee(mine.getId()).getName()).isEqualTo("张三");
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.selectEmployee(other.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限");

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        assertThat(HrTestEnvironment.employeeService.selectEmployee(other.getId()).getName()).isEqualTo("李四");
    }

    @Test
    void selfServiceProfileForbiddenForOthers() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        bo.setUserId(HrTestEnvironment.USER_EMPLOYEE);
        OaEmployeeVo vo = HrTestEnvironment.employeeService.createEmployee(bo);

        OaEmployeeProfileBo profile = new OaEmployeeProfileBo();
        profile.setPhone("13800138001");
        profile.setEmail("self@example.com");

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_OTHER, HrTestEnvironment.DEPT_B, PLAIN_PERMS);
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.updateProfile(vo.getId(), profile))
            .isInstanceOf(ServiceException.class);

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_EMPLOYEE, HrTestEnvironment.DEPT_A, PLAIN_PERMS);
        OaEmployeeVo updated = HrTestEnvironment.employeeService.updateProfile(vo.getId(), profile);
        assertThat(updated.getPhone()).isEqualTo("13800138001");
    }

    @Test
    void failedTransactionRollsBackAllWrites() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeBo ok = new OaEmployeeBo();
        ok.setName("张三");
        ok.setDeptId(HrTestEnvironment.DEPT_A);

        OaEmployeeBo duplicate = new OaEmployeeBo();
        duplicate.setName("李四");
        duplicate.setEmployeeNo("E900");
        duplicate.setDeptId(HrTestEnvironment.DEPT_A);

        assertThatThrownBy(() -> HrTestEnvironment.inTransaction(() -> {
            HrTestEnvironment.employeeService.createEmployee(ok);
            HrTestEnvironment.employeeService.createEmployee(duplicate);
            HrTestEnvironment.employeeService.createEmployee(duplicate);
            return null;
        })).isInstanceOf(ServiceException.class);

        assertThat(HrTestEnvironment.countEmployees()).isZero();
    }

    @Test
    void importWithAnyErrorImportsNothing() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeImportVo good = new OaEmployeeImportVo();
        good.setEmployeeNo("E500");
        good.setName("张三");
        good.setDeptName("管理部");
        good.setHireDate("2026-10-01");

        OaEmployeeImportVo bad = new OaEmployeeImportVo();
        bad.setEmployeeNo("E500");
        bad.setName("李四");
        bad.setDeptName("不存在");
        bad.setHireDate("bad");

        OaImportReportVo report = HrTestEnvironment.employeeService.importEmployees(List.of(good, bad));
        assertThat(report.isImported()).isFalse();
        assertThat(report.getFailedRows()).isEqualTo(1);
        assertThat(HrTestEnvironment.countEmployees()).isZero();
    }

    @Test
    void importAllValidRowsCreatesDrafts() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEmployeeImportVo first = new OaEmployeeImportVo();
        first.setEmployeeNo("E600");
        first.setName("张三");
        first.setDeptName("管理部");
        first.setHireDate("2026-10-01");

        OaEmployeeImportVo second = new OaEmployeeImportVo();
        second.setName("李四");
        second.setDeptName("研发部");
        second.setHireDate("2026/10/02");

        OaImportReportVo report = HrTestEnvironment.employeeService.importEmployees(List.of(first, second));
        assertThat(report.isImported()).isTrue();
        assertThat(HrTestEnvironment.countEmployees()).isEqualTo(2);
        assertThat(HrTestEnvironment.employeeService.selectEmployees(new OaEmployeeBo()))
            .extracting(OaEmployeeVo::getStatus)
            .containsOnly("DRAFT");
    }

    @Test
    void sensitiveExportRequiresGrant() {
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, Set.of("hr:employee:edit"));
        OaEmployeeBo bo = new OaEmployeeBo();
        bo.setName("张三");
        bo.setDeptId(HrTestEnvironment.DEPT_A);
        HrTestEnvironment.employeeService.createEmployee(bo);

        assertThatThrownBy(() -> HrTestEnvironment.employeeService.exportEmployeesFull(bo))
            .isInstanceOf(ServiceException.class);
        assertThat(HrTestEnvironment.employeeService.exportEmployees(bo)).hasSize(1);

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        assertThat(HrTestEnvironment.employeeService.exportEmployeesFull(bo)).hasSize(1);

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_EMPLOYEE, HrTestEnvironment.DEPT_A, PLAIN_PERMS);
        assertThatThrownBy(() -> HrTestEnvironment.employeeService.exportEmployees(bo))
            .isInstanceOf(ServiceException.class);
    }
}
