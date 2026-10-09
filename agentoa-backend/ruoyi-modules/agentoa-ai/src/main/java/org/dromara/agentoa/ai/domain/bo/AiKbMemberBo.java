package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 知识域成员授权（docs/21 AI-M3-01）。
 */
@Data
public class AiKbMemberBo {

    @NotEmpty(message = "成员账号不能为空")
    private List<Long> userIds;
}
