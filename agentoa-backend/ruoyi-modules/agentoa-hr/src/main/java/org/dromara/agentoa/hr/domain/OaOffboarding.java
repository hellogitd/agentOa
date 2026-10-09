package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 离职事件 oa_offboarding。每个员工至多一个生效离职事件。
 */
@Data
@TableName("oa_offboarding")
public class OaOffboarding {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String eventId;

    private String reason;

    private LocalDate lastWorkingDay;

    /** 账号是否已冻结（冻结与离职完成同事务） */
    private Integer accountFrozen;

    private String status;

    private String workflowInstanceId;

    private Long operatorUserId;

    private Date operateTime;

    private Date createTime;
}
