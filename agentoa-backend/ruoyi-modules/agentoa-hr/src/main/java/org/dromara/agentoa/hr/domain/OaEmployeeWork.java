package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工工作经历 oa_employee_work（P1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_work")
public class OaEmployeeWork extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String company;

    private String position;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 离职原因 */
    private String leaveReason;

    private String remark;
}
