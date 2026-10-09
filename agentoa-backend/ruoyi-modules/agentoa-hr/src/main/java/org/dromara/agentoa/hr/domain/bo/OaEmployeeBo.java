package org.dromara.agentoa.hr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 员工档案业务对象（API 规范 3.3 字段）。既用作查询条件，也用作新增/修改入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = OaEmployee.class, reverseConvertGenerate = false)
public class OaEmployeeBo extends BaseEntity {

    private Long id;

    /** 关联系统账号，可空（档案未建账号时） */
    private Long userId;

    /** 工号；新增或导入时可空，由服务端生成 */
    @Size(max = 32, message = "工号长度不能超过{max}个字符")
    private String employeeNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过{max}个字符")
    private String name;

    /** 性别（0女 1男 2未知） */
    @Pattern(regexp = "[012]", message = "性别取值非法")
    private String gender;

    private LocalDate birthDate;

    @Size(max = 18, message = "身份证号长度不能超过{max}个字符")
    private String idCardNo;

    @Size(max = 11, message = "手机号长度不能超过{max}个字符")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    @NotNull(message = "部门不能为空")
    private Long deptId;

    private Long postId;

    @Size(max = 32, message = "职级长度不能超过{max}个字符")
    private String positionLevel;

    private Long directLeaderId;

    /** 状态；新增时固定为 DRAFT，后续只能通过生命周期命令变更 */
    private String status;

    private LocalDate entryDate;

    private LocalDate probationEndDate;

    private String remark;
}
