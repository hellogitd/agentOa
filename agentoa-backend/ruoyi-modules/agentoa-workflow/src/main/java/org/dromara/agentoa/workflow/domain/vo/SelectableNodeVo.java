package org.dromara.agentoa.workflow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发起页可自选审批节点摘要（USER_SELECT）：只暴露 nodeId/名称/是否多选，不下发完整审批链。
 */
@Data
public class SelectableNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 链上节点 ID（对应 assigneeSelections 的 key） */
    private String nodeId;

    private String name;

    /** true = 会签/或签（发起人选 ≥1 人）；false = 单人（选 1 人） */
    private Boolean multiple;
}
