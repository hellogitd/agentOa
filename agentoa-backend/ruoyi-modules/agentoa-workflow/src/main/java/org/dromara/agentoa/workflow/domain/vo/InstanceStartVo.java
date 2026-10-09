package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 发起流程响应（API 规范 4.3）。
 */
@Data
public class InstanceStartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    private String processInstanceId;

    private String businessKey;

    private List<CurrentTaskVo> currentTasks = new ArrayList<>();

    @Data
    public static class CurrentTaskVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private String taskId;

        private String taskName;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long assigneeId;

        private String assigneeName;
    }
}
