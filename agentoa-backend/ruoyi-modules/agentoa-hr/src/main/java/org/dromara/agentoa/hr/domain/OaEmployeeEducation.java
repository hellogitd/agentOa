package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工教育经历 oa_employee_education（P1，API 规范 3.3 education[]）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_education")
public class OaEmployeeEducation extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String school;

    private String major;

    /** 学历 */
    private String education;

    /** 学位 */
    private String degree;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 是否全日制（1是 0否） */
    private Integer isFullTime;

    private String remark;
}
