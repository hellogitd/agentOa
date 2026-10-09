package org.dromara.agentoa.workflow.domain.bo;

import lombok.Data;

import java.util.Map;

/**
 * 业务承接单提交请求体（docs/05 4.3）：{@code lockVersion} 乐观锁 + 发起人自选审批人。
 * <p>
 * {@code assigneeSelections} 为可选字段：{@code nodeId -> userId / [userId]}，
 * 只对链上 {@code assigneeRule=USER_SELECT} 的节点生效，其余键直接拒绝（防变量注入）。
 */
@Data
public class RequestSubmitBo {

    private Integer lockVersion;

    private Map<String, Object> assigneeSelections;
}
