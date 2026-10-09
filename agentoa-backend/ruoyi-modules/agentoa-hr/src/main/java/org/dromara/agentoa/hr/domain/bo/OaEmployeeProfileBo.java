package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工自助字段。仅本人（或 HR/管理员）可修改联系方式，不含 HR 字段。
 */
@Data
public class OaEmployeeProfileBo {

    @Size(max = 11, message = "手机号长度不能超过{max}个字符")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;
}
