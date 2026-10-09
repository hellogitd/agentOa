package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 完成任务（API 规范 4.4）：action 只接受 agree/reject。
 */
@Data
public class TaskCompleteBo {

    @NotBlank(message = "action 不能为空")
    @Pattern(regexp = "agree|reject", message = "action 只能是 agree 或 reject")
    private String action;

    @Size(max = 1000, message = "审批意见长度不能超过{max}个字符")
    private String comment;

    private Integer lockVersion;
}
