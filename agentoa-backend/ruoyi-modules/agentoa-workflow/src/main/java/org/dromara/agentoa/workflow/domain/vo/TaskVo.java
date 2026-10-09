package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class TaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private String taskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    private String processInstanceId;

    private String taskName;

    private String taskDefKey;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneeId;

    private String assigneeName;

    private String businessType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long businessId;

    private String title;

    private Integer instanceStatus;

    private String initiatorName;

    private Date createTime;

    private Date endTime;

    private String comment;
}
