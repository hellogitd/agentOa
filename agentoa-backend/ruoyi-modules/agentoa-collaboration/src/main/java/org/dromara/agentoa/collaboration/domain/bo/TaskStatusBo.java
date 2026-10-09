package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 任务状态变更入参（docs/05 9.3 /tasks/{id}/status） */
@Data
public class TaskStatusBo {

    /** 乐观锁版本 */
    private Integer lockVersion;

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "1|2|3|4|5", message = "状态取值非法")
    private String status;

    /** 变更说明（可选，写入活动记录） */
    private String comment;
}
