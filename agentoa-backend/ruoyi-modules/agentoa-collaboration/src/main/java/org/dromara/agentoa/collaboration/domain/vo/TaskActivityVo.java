package org.dromara.agentoa.collaboration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 任务活动/评论视图 */
@Data
public class TaskActivityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 类型（1创建 2状态变更 3进度 4评论 5成员变更） */
    private Integer activityType;

    private String content;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private String mentionIds;

    private String createTime;
}
