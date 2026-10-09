package org.dromara.agentoa.hr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 员工导入行。工号可空由服务端生成；部门按名称、岗位按编码解析。
 */
@Data
@ExcelIgnoreUnannotated
public class OaEmployeeImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "工号")
    private String employeeNo;

    @ExcelProperty(value = "姓名")
    private String name;

    @ExcelProperty(value = "性别")
    private String gender;

    @ExcelProperty(value = "身份证号")
    private String idCard;

    @ExcelProperty(value = "手机号")
    private String phone;

    @ExcelProperty(value = "邮箱")
    private String email;

    @ExcelProperty(value = "部门名称")
    private String deptName;

    @ExcelProperty(value = "岗位编码")
    private String positionCode;

    @ExcelProperty(value = "入职日期")
    private String hireDate;

    @ExcelProperty(value = "备注")
    private String remark;
}
