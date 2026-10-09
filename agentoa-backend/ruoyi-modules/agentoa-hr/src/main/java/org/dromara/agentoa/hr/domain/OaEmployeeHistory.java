package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 员工状态变更历史 oa_employee_history。event_id 幂等，保证一次业务命令只有一个生效事件。
 */
@Data
@TableName("oa_employee_history")
public class OaEmployeeHistory {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String eventId;

    /** CREATE / UPDATE / ONBOARDING / PROBATION / OFFBOARDING */
    private String eventType;

    private String fromStatus;

    private String toStatus;

    private String detail;

    private String workflowInstanceId;

    private Long operatorUserId;

    private Date operateTime;
}
