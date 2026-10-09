package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;
import org.dromara.agentoa.hr.service.support.HrImportValidator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入校验：逐行错误报告，存在错误行时不允许部分成功。
 */
class HrImportValidatorTest {

    private static final Map<String, Long> DEPTS = Map.of("管理部", 10L, "研发部", 20L);
    private static final Map<String, Long> POSTS = Map.of("DEV", 1L);

    private OaEmployeeImportVo row(String no, String name, String dept, String hire) {
        OaEmployeeImportVo vo = new OaEmployeeImportVo();
        vo.setEmployeeNo(no);
        vo.setName(name);
        vo.setDeptName(dept);
        vo.setHireDate(hire);
        return vo;
    }

    @Test
    void validRowPasses() {
        OaImportReportVo report = HrImportValidator.validate(
            List.of(row("E001", "张三", "管理部", "2026-10-01")), Set.of(), DEPTS, POSTS, true);
        assertThat(report.getFailedRows()).isZero();
        assertThat(report.getSuccessRows()).isEqualTo(1);
        assertThat(report.getRows().get(0).isValid()).isTrue();
    }

    @Test
    void duplicateAndMissingNumberFlagged() {
        OaImportReportVo report = HrImportValidator.validate(List.of(
            row("E001", "张三", "管理部", "2026-10-01"),
            row("E001", "李四", "管理部", "2026-10-01"),
            row(null, null, "不存在的部门", "bad-date")),
            Set.of("E001"), DEPTS, POSTS, true);
        assertThat(report.getTotalRows()).isEqualTo(3);
        assertThat(report.getFailedRows()).isEqualTo(3);
        assertThat(report.getRows().get(0).getErrors()).contains("工号已存在");
        assertThat(report.getRows().get(1).getErrors()).contains("工号已存在", "工号在文件内重复");
        OaImportReportVo.Row bad = report.getRows().get(2);
        assertThat(bad.getErrors()).contains("姓名不能为空", "部门不存在", "入职日期非法");
    }

    @Test
    void invalidDateAndDept() {
        OaImportReportVo report = HrImportValidator.validate(
            List.of(row("E010", "王五", "研发部", "2026/13/40")), Set.of(), DEPTS, POSTS, true);
        assertThat(report.getRows().get(0).getErrors()).contains("入职日期非法");
        assertThat(HrImportValidator.parseDate("2026/10/01")).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void sensitiveFieldsRequirePermission() {
        OaEmployeeImportVo vo = row("E020", "赵六", "管理部", "2026-10-01");
        vo.setIdCard("110101199505151234");
        vo.setPhone("13800138000");

        OaImportReportVo denied = HrImportValidator.validate(List.of(vo), Set.of(), DEPTS, POSTS, false);
        assertThat(denied.getRows().get(0).getErrors())
            .contains("无敏感字段权限，不能导入身份证号", "无敏感字段权限，不能导入手机号");

        OaImportReportVo allowed = HrImportValidator.validate(List.of(vo), Set.of(), DEPTS, POSTS, true);
        assertThat(allowed.getFailedRows()).isZero();

        OaEmployeeImportVo bad = row("E021", "钱七", "管理部", "2026-10-01");
        bad.setIdCard("123");
        bad.setPhone("abc");
        OaImportReportVo invalid = HrImportValidator.validate(List.of(bad), Set.of(), DEPTS, POSTS, true);
        assertThat(invalid.getRows().get(0).getErrors())
            .contains("身份证号格式非法", "手机号格式非法");
    }

    @Test
    void unknownPostCodeFlagged() {
        OaEmployeeImportVo vo = row("E030", "孙八", "管理部", "2026-10-01");
        vo.setPositionCode("NOPE");
        OaImportReportVo report = HrImportValidator.validate(List.of(vo), Set.of(), DEPTS, POSTS, true);
        assertThat(report.getRows().get(0).getErrors()).contains("岗位编码不存在");
    }
}
