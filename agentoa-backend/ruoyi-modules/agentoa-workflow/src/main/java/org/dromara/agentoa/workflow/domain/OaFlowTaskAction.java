package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_flow_task_action")
public class OaFlowTaskAction {

    @TableId(value = "id")
    private Long id;

    private Long instanceId;

    private String flowableTaskId;

    private String taskName;

    private String taskDefKey;

    /** agree/reject/transfer/cancel */
    private String action;

    private Long operatorUserId;

    private String operatorName;

    private Long oldAssigneeId;

    private String oldAssigneeName;

    private Long newAssigneeId;

    private String newAssigneeName;

    private String comment;

    private Date actionTime;
}
