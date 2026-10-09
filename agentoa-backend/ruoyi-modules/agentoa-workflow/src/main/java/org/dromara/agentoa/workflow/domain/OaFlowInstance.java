package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_instance")
public class OaFlowInstance extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long definitionId;

    private Long definitionVersionId;

    private Long formVersionId;

    private String formSchemaSnapshot;

    private String formData;

    private String businessType;

    private Long businessId;

    private String businessKey;

    private Integer submissionNo;

    private String title;

    private Long initiatorUserId;

    private String initiatorName;

    private Long initiatorDeptId;

    private Integer priority;

    /** 1审批中 2已通过 3已拒绝 4已撤销 5已挂起 6已终止 */
    private Integer status;

    private String flowableProcInstId;

    private String currentTaskName;

    private String currentAssignees;

    private Date startTime;

    private Date endTime;

    private Long duration;

    private Integer lockVersion;

    /** 是否超时提醒过（P1，WF-10 独立标志） */
    private Integer isTimeout;

    private String remark;
}
