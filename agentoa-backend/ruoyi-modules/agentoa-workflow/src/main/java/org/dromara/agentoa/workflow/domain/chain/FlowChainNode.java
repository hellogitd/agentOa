package org.dromara.agentoa.workflow.domain.chain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 审批链节点（结构化，钉钉式）。
 * <ul>
 *   <li>{@code approve} 审批节点：assigneeRule + signMode</li>
 *   <li>{@code cc} 抄送节点：assigneeRule，实例启动时生成抄送记录</li>
 *   <li>{@code branch} 条件分支：互斥分支列表，最后一支可为默认分支（condition 为空）</li>
 * </ul>
 */
@Data
public class FlowChainNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String TYPE_APPROVE = "approve";
    public static final String TYPE_CC = "cc";
    public static final String TYPE_BRANCH = "branch";

    /** 节点 ID（同一链内唯一，编译为 BPMN 元素 ID） */
    private String id;

    /** 节点类型 approve/cc/branch */
    private String type;

    /** 节点名称（编译为 BPMN name） */
    private String name;

    /**
     * UI 语义标记（如 {@code handle}=办理）：仅用于向导图标/文案区分「审批」与「办理」，
     * 编译器忽略该字段，不改变节点类型与运行时语义。
     */
    private String kind;

    /** 办理人规则 SELF/LEADER/DEPT_HEAD/ROLE:key/WHITELIST:id,id/USER_SELECT */
    private String assigneeRule;

    /** 签署方式 SINGLE/COUNTERSIGN/EITHERSIGN，仅 approve 生效 */
    private String signMode;

    /** 条件分支的分支列表，仅 branch 生效 */
    private List<FlowBranch> branches = new ArrayList<>();

    /** 分支内的嵌套节点 */
    private List<FlowChainNode> nodes = new ArrayList<>();

    @Data
    public static class FlowBranch implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String id;

        private String name;

        /** 为空表示默认分支（else） */
        private FlowCondition condition;

        private List<FlowChainNode> nodes = new ArrayList<>();
    }
}
