package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class InstanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String processKey;

    private String processName;

    private Integer definitionVersionNo;

    private String businessType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long businessId;

    private String businessKey;

    private Integer submissionNo;

    private String title;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long initiatorUserId;

    private String initiatorName;

    private Integer priority;

    /** 1审批中 2已通过 3已拒绝 4已撤销 5已挂起 6已终止 */
    private Integer status;

    private String currentTaskName;

    private String currentAssignees;

    private Date startTime;

    private Date endTime;

    private Long duration;

    private Integer lockVersion;
}
