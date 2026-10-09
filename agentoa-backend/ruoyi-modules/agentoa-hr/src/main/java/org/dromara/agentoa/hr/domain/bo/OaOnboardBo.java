package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 入职命令（API 规范 3.4）。创建草稿档案并立即办理入职。
 */
@Data
public class OaOnboardBo {

    /** 工号，可空由服务端生成 */
    @Size(max = 32, message = "工号长度不能超过{max}个字符")
    private String employeeNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过{max}个字符")
    private String name;

    @Pattern(regexp = "[012]", message = "性别取值非法")
    private String gender;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
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

    @NotNull(message = "入职日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationEndDate;

    /** 预留流程实例 ID */
    @Size(max = 64, message = "流程实例 ID 长度不能超过{max}个字符")
    private String workflowInstanceId;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
