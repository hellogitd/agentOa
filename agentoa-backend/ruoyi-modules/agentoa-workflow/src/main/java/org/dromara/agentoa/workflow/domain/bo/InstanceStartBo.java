package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 发起流程（API 规范 4.3）：业务数据由承接单持有，服务端生成表单快照与流程变量。
 * 纯 OA 表单（通用承接）应显式传 definitionId，避免同业务类型下多定义歧义。
 */
@Data
public class InstanceStartBo {

    /** 目标流程定义 ID（可选；通用承接/纯 OA 表单建议必填） */
    private Long definitionId;

    @NotNull(message = "businessId 不能为空")
    private Long businessId;

    @Size(max = 64, message = "businessType 长度不能超过{max}个字符")
    private String businessType;

    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    private Integer priority;

    private Integer lockVersion;

    /**
     * 发起人自选审批人（USER_SELECT 节点）：{@code nodeId -> userId / [userId]}。
     * 只对链上 assigneeRule=USER_SELECT 的节点生效，其余键直接拒绝（防变量注入）。
     */
    private Map<String, Object> assigneeSelections;
}
