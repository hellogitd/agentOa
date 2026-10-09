package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskTransferBo {

    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;

    @Size(max = 500, message = "转办原因长度不能超过{max}个字符")
    private String reason;
}
