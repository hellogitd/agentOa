package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工业务档案 oa_employee。登录账号字段保存在 sys_user，此处只通过 user_id 关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee")
public class OaEmployee extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    /** 关联系统账号 */
    private Long userId;

    /** 工号，数据库唯一约束 */
    private String employeeNo;

    private String name;

    /** 性别（0女 1男 2未知） */
    private String gender;

    private LocalDate birthDate;

    private String idCardNo;

    private String phone;

    private String email;

    private Long deptId;

    private Long postId;

    private String positionLevel;

    /** 直属主管账号 ID */
    private Long directLeaderId;

    /** 员工状态 EmployeeStatus */
    private String status;

    private LocalDate entryDate;

    private LocalDate probationEndDate;

    private LocalDate regularDate;

    private LocalDate leaveDate;

    /** 预留流程模块关联 */
    private String workflowInstanceId;

    /** 基本工资密文（含 nonce/tag），P1 调薪使用 */
    private String baseSalary;

    /** 薪资密钥版本 */
    private String salaryKeyVersion;

    private String remark;
}
