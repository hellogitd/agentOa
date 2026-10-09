package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 入职事件 oa_onboarding。每个员工至多一个入职事件。
 */
@Data
@TableName("oa_onboarding")
public class OaOnboarding {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String eventId;

    private LocalDate entryDate;

    private LocalDate probationEndDate;

    private Long deptId;

    private Long postId;

    private String status;

    private String workflowInstanceId;

    private Long operatorUserId;

    private Date operateTime;

    private Date createTime;
}
