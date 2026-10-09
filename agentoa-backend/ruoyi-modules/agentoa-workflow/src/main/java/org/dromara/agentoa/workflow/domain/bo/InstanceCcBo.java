package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 抄送命令（P1）。
 */
@Data
public class InstanceCcBo {

    @NotEmpty(message = "被抄送人不能为空")
    private List<Long> userIds;

    @Size(max = 500, message = "留言长度不能超过{max}个字符")
    private String comment;
}
