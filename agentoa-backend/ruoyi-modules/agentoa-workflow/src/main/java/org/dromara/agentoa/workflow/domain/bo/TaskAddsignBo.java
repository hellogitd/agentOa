package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 加签命令（P1，API 规范 4.4 addsign）。
 * <p>
 * before=前加签：加签人成为当前节点共同办理人（或签）；
 * after=后加签：当前办理人同意后按顺序转给加签人逐一处理再推进。
 */
@Data
public class TaskAddsignBo {

    @NotEmpty(message = "加签人不能为空")
    private List<Long> assigneeIds;

    @Pattern(regexp = "before|after", message = "加签位置取值非法")
    private String position;

    @Size(max = 255, message = "原因长度不能超过{max}个字符")
    private String reason;
}
