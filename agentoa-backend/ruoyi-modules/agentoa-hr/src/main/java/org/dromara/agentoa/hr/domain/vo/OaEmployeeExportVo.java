package org.dromara.agentoa.hr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployee;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 花名册导出对象（不含敏感字段）。持有 hr:employee:sensitive 授权时使用 {@link OaEmployeeExportFullVo}。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = OaEmployee.class)
public class OaEmployeeExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "工号")
    private String employeeNo;

    @ExcelProperty(value = "姓名")
    private String name;

    @ExcelProperty(value = "性别")
    private String gender;

    @ExcelProperty(value = "邮箱")
    private String email;

    @ExcelProperty(value = "部门")
    private String deptName;

    @ExcelProperty(value = "岗位")
    private String postName;

    @ExcelProperty(value = "职级")
    private String positionLevel;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "入职日期")
    private LocalDate entryDate;

    @ExcelProperty(value = "转正日期")
    private LocalDate regularDate;

    @ExcelProperty(value = "离职日期")
    private LocalDate leaveDate;
}
