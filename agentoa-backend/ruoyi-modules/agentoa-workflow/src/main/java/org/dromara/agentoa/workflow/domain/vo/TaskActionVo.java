package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class TaskActionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String taskName;

    /** agree/reject/transfer/cancel */
    private String action;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorUserId;

    private String operatorName;

    private String oldAssigneeName;

    private String newAssigneeName;

    private String comment;

    private Date actionTime;
}
