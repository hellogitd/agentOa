package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 退回命令（P1，API 规范 4.4 return）。
 * <p>
 * 目标二选一：toInitiator 退回到发起节点；targetTaskKey 退回到历史节点。
 */
@Data
public class TaskReturnBo {

    /** 退回到发起人节点 */
    private Boolean toInitiator;

    /** 目标历史节点 taskDefinitionKey */
    @Size(max = 64, message = "目标节点长度不能超过{max}个字符")
    private String targetTaskKey;

    @Size(max = 500, message = "意见长度不能超过{max}个字符")
    private String comment;

    private Integer lockVersion;
}
