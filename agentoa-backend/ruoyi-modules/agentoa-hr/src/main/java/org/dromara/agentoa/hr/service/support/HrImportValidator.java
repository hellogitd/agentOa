package org.dromara.agentoa.hr.service.support;

import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 导入校验（纯函数）：逐行返回错误；存在任一错误行时调用方不得导入任何数据。
 */
public final class HrImportValidator {

    private static final Pattern ID_CARD = Pattern.compile("^(\\d{15}|\\d{17}[\\dXx])$");
    private static final Pattern PHONE = Pattern.compile("^\\d{11}$");
    private static final Pattern EMPLOYEE_NO = Pattern.compile("^[A-Za-z0-9\\-]{1,32}$");
    private static final DateTimeFormatter[] DATE_PATTERNS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    private HrImportValidator() {
    }

    /**
     * @param rows              导入行
     * @param existingNos       库中已存在的工号
     * @param deptIdByName      部门名称到 ID 的映射（不存在的部门校验失败）
     * @param positionIdByCode  岗位编码到 ID 的映射
     * @param sensitiveAllowed  是否允许写入身份证/手机号（hr:employee:sensitive 授权）
     */
    public static OaImportReportVo validate(List<OaEmployeeImportVo> rows,
                                            Set<String> existingNos,
                                            Map<String, Long> deptIdByName,
                                            Map<String, Long> positionIdByCode,
                                            boolean sensitiveAllowed) {
        OaImportReportVo report = new OaImportReportVo();
        Set<String> seenNos = new HashSet<>();
        if (rows == null || rows.isEmpty()) {
            return report;
        }
        for (int i = 0; i < rows.size(); i++) {
            OaEmployeeImportVo row = rows.get(i);
            OaImportReportVo.Row result = new OaImportReportVo.Row();
            result.setRowNumber(i + 1);
            result.setEmployeeNo(trim(row.getEmployeeNo()));
            result.setName(trim(row.getName()));
            result.setValid(true);

            if (isBlank(row.getName())) {
                result.addError("姓名不能为空");
            }
            String no = trim(row.getEmployeeNo());
            if (!isBlank(no)) {
                if (!EMPLOYEE_NO.matcher(no).matches()) {
                    result.addError("工号格式非法");
                } else {
                    String key = no.toUpperCase();
                    if (existingNos != null && existingNos.contains(key)) {
                        result.addError("工号已存在");
                    }
                    if (!seenNos.add(key)) {
                        result.addError("工号在文件内重复");
                    }
                }
            }
            if (!isBlank(row.getGender()) && !List.of("0", "1", "2").contains(trim(row.getGender()))) {
                result.addError("性别取值非法");
            }
            if (!isBlank(row.getIdCard())) {
                if (!sensitiveAllowed) {
                    result.addError("无敏感字段权限，不能导入身份证号");
                } else if (!ID_CARD.matcher(trim(row.getIdCard())).matches()) {
                    result.addError("身份证号格式非法");
                }
            }
            if (!isBlank(row.getPhone())) {
                if (!sensitiveAllowed) {
                    result.addError("无敏感字段权限，不能导入手机号");
                } else if (!PHONE.matcher(trim(row.getPhone())).matches()) {
                    result.addError("手机号格式非法");
                }
            }
            if (!isBlank(row.getEmail()) && !trim(row.getEmail()).contains("@")) {
                result.addError("邮箱格式非法");
            }
            if (isBlank(row.getDeptName())) {
                result.addError("部门不能为空");
            } else if (deptIdByName == null || !deptIdByName.containsKey(trim(row.getDeptName()))) {
                result.addError("部门不存在");
            }
            if (!isBlank(row.getPositionCode())
                && (positionIdByCode == null || !positionIdByCode.containsKey(trim(row.getPositionCode())))) {
                result.addError("岗位编码不存在");
            }
            if (isBlank(row.getHireDate())) {
                result.addError("入职日期不能为空");
            } else if (parseDate(trim(row.getHireDate())) == null) {
                result.addError("入职日期非法");
            }

            if (!result.isValid()) {
                report.setFailedRows(report.getFailedRows() + 1);
            } else {
                report.setSuccessRows(report.getSuccessRows() + 1);
            }
            report.getRows().add(result);
        }
        report.setTotalRows(rows.size());
        return report;
    }

    public static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter pattern : DATE_PATTERNS) {
            try {
                return LocalDate.parse(trim(value), pattern);
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
