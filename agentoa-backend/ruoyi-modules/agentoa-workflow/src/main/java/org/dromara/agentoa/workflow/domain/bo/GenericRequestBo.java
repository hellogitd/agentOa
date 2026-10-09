package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 通用 OA 申请载荷（M5）：纯 OA 表单直接发起/草稿创建。
 */
@Data
public class GenericRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "definitionId 不能为空")
    private Long definitionId;

    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    /** 表单数据 JSON（按定义的表单 Schema 填写） */
    private String formData;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

    /**
     * 发起人自选审批人（USER_SELECT 节点）：{@code nodeId -> userId / [userId]}。
     * 只对链上 assigneeRule=USER_SELECT 的节点生效，其余键直接拒绝（防变量注入）。
     */
    private Map<String, Object> assigneeSelections;
}
