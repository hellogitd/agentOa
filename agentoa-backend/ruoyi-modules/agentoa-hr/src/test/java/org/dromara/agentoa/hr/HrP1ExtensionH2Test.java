package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.OaContract;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaEmployeeChange;
import org.dromara.agentoa.hr.domain.bo.OaChangeBo;
import org.dromara.agentoa.hr.domain.bo.OaContractBo;
import org.dromara.agentoa.hr.domain.bo.OaEducationBo;
import org.dromara.agentoa.hr.domain.bo.OaWorkBo;
import org.dromara.agentoa.hr.domain.vo.OaChangeVo;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;
import org.dromara.agentoa.hr.service.support.SalaryCipher;
import org.dromara.agentoa.hr.support.HrTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 集成测试（P1）：调岗调薪生效与去重、合同与续签提醒、教育/工作经历与自助越权。
 */
class HrP1ExtensionH2Test {

    private static final Set<String> HR_PERMS = Set.of(
        "hr:employee:edit", "hr:change:add", "hr:change:query", "hr:change:list",
        "hr:change:sensitive", "hr:contract:list", "hr:contract:query");
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

    private OaEmployee seedEmployee(String name) {
        OaEmployee employee = new OaEmployee();
        employee.setName(name);
        employee.setEmployeeNo("E" + System.nanoTime() % 100000000);
        employee.setDeptId(HrTestEnvironment.DEPT_A);
        employee.setStatus("ACTIVE");
        HrTestEnvironment.employees.insert(employee);
        return employee;
    }

    // ---------------------------------------------------------------- 调岗调薪

    @Test
    void transferAppliesImmediatelyAndWritesHistory() {
        OaEmployee employee = seedEmployee("张三");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaChangeBo bo = new OaChangeBo();
        bo.setEmployeeId(employee.getId());
        bo.setChangeType(OaEmployeeChange.TYPE_TRANSFER);
        bo.setEffectiveDate(LocalDate.now());
        bo.setNewDeptId(HrTestEnvironment.DEPT_B);
        bo.setNewPositionLevel("P7");
        bo.setReason("组织调整");
        OaChangeVo vo = HrTestEnvironment.changeService.createChange(bo);

        assertThat(vo.getApplied()).isEqualTo(1);
        OaEmployee after = HrTestEnvironment.employee(employee.getId());
        assertThat(after.getDeptId()).isEqualTo(HrTestEnvironment.DEPT_B);
        assertThat(after.getPositionLevel()).isEqualTo("P7");
        assertThat(HrTestEnvironment.historyOf(employee.getId()))
            .extracting(h -> h.getEventId())
            .contains("CHANGE:" + vo.getId());
    }

    @Test
    void futureChangeAppliesOnceOnDueDate() {
        OaEmployee employee = seedEmployee("李四");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaChangeBo bo = new OaChangeBo();
        bo.setEmployeeId(employee.getId());
        bo.setChangeType(4);
        bo.setEffectiveDate(LocalDate.now().plusDays(1));
        bo.setNewSalary(new BigDecimal("18000.00"));
        bo.setSourceRequestId(9001L);
        OaChangeVo vo = HrTestEnvironment.changeService.createChange(bo);

        assertThat(vo.getApplied()).isEqualTo(0);
        assertThat(HrTestEnvironment.employee(employee.getId()).getBaseSalary()).isNull();

        // 到期执行一次
        assertThat(HrTestEnvironment.changeService.applyDueChanges()).isZero();

        // 模拟到期（直接改生效日期后重放）
        var change = HrTestEnvironment.changes.selectById(vo.getId());
        change.setEffectiveDate(LocalDate.now());
        HrTestEnvironment.changes.updateById(change);
        assertThat(HrTestEnvironment.changeService.applyDueChanges()).isEqualTo(1);

        OaEmployee after = HrTestEnvironment.employee(employee.getId());
        assertThat(after.getBaseSalary()).isNotBlank();
        assertThat(SalaryCipher.decrypt(after.getBaseSalary())).isEqualTo("18000.00");

        // 幂等：再次执行不重复生效
        assertThat(HrTestEnvironment.changeService.applyDueChanges()).isZero();
        assertThat(HrTestEnvironment.historyOf(employee.getId()))
            .filteredOn(h -> "CHANGE".equals(h.getEventType()))
            .hasSize(1);
    }

    @Test
    void duplicateSourceRequestRejected() {
        OaEmployee employee = seedEmployee("王五");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaChangeBo first = new OaChangeBo();
        first.setEmployeeId(employee.getId());
        first.setChangeType(3);
        first.setEffectiveDate(LocalDate.now());
        first.setNewPositionLevel("P6");
        first.setSourceRequestId(7001L);
        HrTestEnvironment.changeService.createChange(first);

        OaChangeBo second = new OaChangeBo();
        second.setEmployeeId(employee.getId());
        second.setChangeType(3);
        second.setEffectiveDate(LocalDate.now());
        second.setNewPositionLevel("P8");
        second.setSourceRequestId(7001L);
        assertThatThrownBy(() -> HrTestEnvironment.changeService.createChange(second))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已存在");
    }

    @Test
    void salaryMaskedWithoutSensitivePermission() {
        OaEmployee employee = seedEmployee("赵六");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaChangeBo bo = new OaChangeBo();
        bo.setEmployeeId(employee.getId());
        bo.setChangeType(4);
        bo.setEffectiveDate(LocalDate.now());
        bo.setNewSalary(new BigDecimal("20000.00"));
        OaChangeVo created = HrTestEnvironment.changeService.createChange(bo);
        assertThat(created.getNewSalary()).isEqualTo("20000.00");

        // 无敏感权限：脱敏显示
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A,
            Set.of("hr:change:query", "hr:change:list"));
        OaChangeVo masked = HrTestEnvironment.changeService.selectChange(created.getId());
        assertThat(masked.getNewSalary()).isEqualTo(SalaryCipher.mask());
    }

    @Test
    void changeRequiresPermission() {
        OaEmployee employee = seedEmployee("钱七");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_EMPLOYEE, HrTestEnvironment.DEPT_A, PLAIN_PERMS);
        OaChangeBo bo = new OaChangeBo();
        bo.setEmployeeId(employee.getId());
        bo.setChangeType(3);
        bo.setEffectiveDate(LocalDate.now());
        bo.setNewPositionLevel("P9");
        assertThatThrownBy(() -> HrTestEnvironment.changeService.createChange(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限");
    }

    // ---------------------------------------------------------------- 合同

    @Test
    void contractLifecycleAndExpiringReminder() {
        OaEmployee employee = seedEmployee("合同工");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaContractBo bo = new OaContractBo();
        bo.setEmployeeId(employee.getId());
        bo.setContractNo("HT-001");
        bo.setContractType("FIXED_TERM");
        bo.setStartDate(LocalDate.now().minusYears(1));
        bo.setEndDate(LocalDate.now().plusDays(10));
        OaContractVo vo = HrTestEnvironment.contractService.createContract(bo);
        assertThat(vo.getStatus()).isEqualTo(1);
        assertThat(vo.getDaysToExpire()).isEqualTo(10);

        List<OaContractVo> expiring = HrTestEnvironment.contractService.selectExpiring(30);
        assertThat(expiring).extracting(OaContractVo::getId).contains(vo.getId());

        // 过期合同状态由服务端推导
        OaContractBo expired = new OaContractBo();
        expired.setEmployeeId(employee.getId());
        expired.setContractNo("HT-002");
        expired.setContractType("FIXED_TERM");
        expired.setStartDate(LocalDate.now().minusYears(3));
        expired.setEndDate(LocalDate.now().minusDays(1));
        OaContractVo expiredVo = HrTestEnvironment.contractService.createContract(expired);
        assertThat(expiredVo.getStatus()).isEqualTo(OaContract.STATUS_EXPIRED);

        // 终止覆盖
        OaContractBo terminate = new OaContractBo();
        terminate.setContractType("FIXED_TERM");
        terminate.setStartDate(vo.getStartDate());
        terminate.setEndDate(vo.getEndDate());
        terminate.setStatus(OaContract.STATUS_TERMINATED);
        OaContractVo terminated = HrTestEnvironment.contractService.updateContract(vo.getId(), terminate);
        assertThat(terminated.getStatus()).isEqualTo(OaContract.STATUS_TERMINATED);
    }

    @Test
    void duplicateContractNoRejected() {
        OaEmployee employee = seedEmployee("重号");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaContractBo bo = new OaContractBo();
        bo.setEmployeeId(employee.getId());
        bo.setContractNo("HT-DUP");
        bo.setContractType("FIXED_TERM");
        bo.setStartDate(LocalDate.now());
        HrTestEnvironment.contractService.createContract(bo);
        assertThatThrownBy(() -> HrTestEnvironment.contractService.createContract(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已存在");
    }

    @Test
    void contractEndDateBeforeStartDateRejected() {
        OaEmployee employee = seedEmployee("日期错");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaContractBo bo = new OaContractBo();
        bo.setEmployeeId(employee.getId());
        bo.setContractType("FIXED_TERM");
        bo.setStartDate(LocalDate.now());
        bo.setEndDate(LocalDate.now().minusDays(5));
        assertThatThrownBy(() -> HrTestEnvironment.contractService.createContract(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能早于");
    }

    // ---------------------------------------------------------------- 教育 / 工作经历

    @Test
    void educationAndWorkCrud() {
        OaEmployee employee = seedEmployee("经历");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);

        OaEducationBo education = new OaEducationBo();
        education.setSchool("测试大学");
        education.setMajor("计算机");
        education.setEducation("本科");
        OaEducationVo educationVo = HrTestEnvironment.experienceService.createEducation(employee.getId(), education);
        assertThat(HrTestEnvironment.experienceService.selectEducations(employee.getId())).hasSize(1);

        OaWorkBo work = new OaWorkBo();
        work.setCompany("原公司");
        work.setPosition("工程师");
        OaWorkVo workVo = HrTestEnvironment.experienceService.createWork(employee.getId(), work);
        assertThat(HrTestEnvironment.experienceService.selectWorks(employee.getId())).hasSize(1);

        OaEducationBo update = new OaEducationBo();
        update.setSchool("测试大学");
        update.setMajor("软件工程");
        update.setEducation("硕士");
        OaEducationVo updated = HrTestEnvironment.experienceService.updateEducation(
            employee.getId(), educationVo.getId(), update);
        assertThat(updated.getEducation()).isEqualTo("硕士");

        HrTestEnvironment.experienceService.deleteWork(employee.getId(), workVo.getId());
        assertThat(HrTestEnvironment.experienceService.selectWorks(employee.getId())).isEmpty();
    }

    @Test
    void selfServiceCanMaintainOwnExperienceButNotOthers() {
        OaEmployee own = seedEmployee("本人");
        own.setUserId(HrTestEnvironment.USER_EMPLOYEE);
        HrTestEnvironment.employees.updateById(own);
        OaEmployee other = seedEmployee("他人");

        HrTestEnvironment.loginAs(HrTestEnvironment.USER_EMPLOYEE, HrTestEnvironment.DEPT_A, PLAIN_PERMS);
        OaEducationBo education = new OaEducationBo();
        education.setSchool("我的大学");
        HrTestEnvironment.experienceService.createEducation(own.getId(), education);

        assertThatThrownBy(() -> HrTestEnvironment.experienceService.createEducation(other.getId(), education))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有权限");
    }

    @Test
    void crossEmployeeRecordUpdateRejected() {
        OaEmployee first = seedEmployee("甲");
        OaEmployee second = seedEmployee("乙");
        HrTestEnvironment.loginAs(HrTestEnvironment.USER_HR, HrTestEnvironment.DEPT_A, HR_PERMS);
        OaEducationBo education = new OaEducationBo();
        education.setSchool("A 校");
        OaEducationVo vo = HrTestEnvironment.experienceService.createEducation(first.getId(), education);

        assertThatThrownBy(() -> HrTestEnvironment.experienceService.updateEducation(
            second.getId(), vo.getId(), education))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }
}
