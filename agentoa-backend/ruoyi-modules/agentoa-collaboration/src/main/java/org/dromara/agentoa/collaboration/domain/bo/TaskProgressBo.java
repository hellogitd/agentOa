package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 任务进度入参（docs/05 9.3 /tasks/{id}/progress） */
@Data
public class TaskProgressBo {

    /** 乐观锁版本 */
    private Integer lockVersion;

    @NotNull(message = "进度不能为空")
    @Min(value = 0, message = "进度取值范围 0-100")
    @Max(value = 100, message = "进度取值范围 0-100")
    private Integer progress;

    private String comment;
}
