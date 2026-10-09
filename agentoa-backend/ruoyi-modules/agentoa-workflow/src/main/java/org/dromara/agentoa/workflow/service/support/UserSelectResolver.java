package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.assigner.AssigneeResolver;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.domain.enums.FlowSignMode;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发起人自选办理人（USER_SELECT）：校验发起端 {@code assigneeSelections} 并转成流程变量。
 * <p>
 * 安全边界（docs/05 4.2）：只映射到链上 USER_SELECT 节点的专用变量 {@code userSelect_<nodeId>}，
 * 拒绝任意节点 ID（防变量注入）；userId 做存在性 + 启用校验；选择结果缺失/为空确定性报错，
 * 不回退任意人。
 * <p>
 * 数量语义：{@code SINGLE} 选 1 人；{@code COUNTERSIGN/EITHERSIGN} 选 ≥1 人（多选）。
 */
@Component
@RequiredArgsConstructor
public class UserSelectResolver {

    /** 单节点自选人数上限（多选模式） */
    public static final int MAX_SELECTIONS_PER_NODE = 20;

    private final WorkflowIdentityReadMapper identityMapper;

    /**
     * 校验并转换发起人自选结果。
     *
     * @param chainJson    流程定义的审批链 JSON（定位链上 USER_SELECT 节点）
     * @param userSelects  nodeId -> Long / List&lt;Long&gt;（可为空，表示发起端未上送）
     * @return 流程变量：{@code userSelect_<nodeId>} -> Long（SINGLE）或 List&lt;Long&gt;（会签/或签）
     */
    public Map<String, Object> toVariables(String chainJson, Map<String, Object> userSelects) {
        Map<String, FlowChainNode> selectable = collectSelectableNodes(chainJson);
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> provided = userSelects == null ? Map.of() : userSelects;
        for (String key : provided.keySet()) {
            if (!selectable.containsKey(key)) {
                throw new ServiceException("自选审批人节点不存在或不可自选: " + key, 400);
            }
        }
        for (Map.Entry<String, FlowChainNode> entry : selectable.entrySet()) {
            String nodeId = entry.getKey();
            FlowChainNode node = entry.getValue();
            Object raw = provided.get(nodeId);
            List<Long> users = AssigneeResolver.normalizeSelection(raw);
            FlowSignMode mode = FlowSignMode.from(node.getSignMode());
            if (users.isEmpty()) {
                throw new ServiceException("节点「" + node.getName() + "」未选择审批人", 400);
            }
            if (mode == FlowSignMode.SINGLE && users.size() != 1) {
                throw new ServiceException("节点「" + node.getName() + "」只能选择 1 名审批人", 400);
            }
            if (users.size() > MAX_SELECTIONS_PER_NODE) {
                throw new ServiceException("节点「" + node.getName() + "」自选审批人超过上限 " + MAX_SELECTIONS_PER_NODE, 400);
            }
            List<Long> enabled = identityMapper.selectEnabledUserIds(users);
            if (enabled == null || enabled.size() != users.size()) {
                throw new ServiceException("节点「" + node.getName() + "」自选审批人不存在或已停用", 400);
            }
            variables.put(AssigneeResolver.USER_SELECT_VARIABLE_PREFIX + nodeId,
                mode == FlowSignMode.SINGLE ? users.get(0) : List.copyOf(users));
        }
        return variables;
    }

    /** 提取链上全部 USER_SELECT 节点：nodeId -> 节点 */
    public static Map<String, FlowChainNode> collectSelectableNodes(String chainJson) {
        Map<String, FlowChainNode> nodes = new LinkedHashMap<>();
        if (chainJson == null || chainJson.isBlank()) {
            return nodes;
        }
        FlowChainConfig config;
        try {
            config = PlainJson.parse(chainJson, FlowChainConfig.class);
        } catch (Exception e) {
            throw new ServiceException("审批链配置解析失败", 500);
        }
        walk(config == null ? null : config.getNodes(), nodes);
        return nodes;
    }

    private static void walk(List<FlowChainNode> nodes, Map<String, FlowChainNode> out) {
        if (nodes == null) {
            return;
        }
        for (FlowChainNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (FlowChainNode.TYPE_APPROVE.equals(node.getType())
                && FlowChainCompiler.isUserSelect(node.getAssigneeRule())) {
                out.put(node.getId(), node);
            }
            if (node.getBranches() != null) {
                for (FlowChainNode.FlowBranch branch : node.getBranches()) {
                    if (branch != null) {
                        walk(branch.getNodes(), out);
                    }
                }
            }
            walk(node.getNodes(), out);
        }
    }

    /** 发起端可选节点摘要（LaunchDefinitionVo.selectableNodes） */
    public record SelectableNode(String nodeId, String name, boolean multiple) {
    }

    public static List<SelectableNode> selectableSummaries(String chainJson) {
        List<SelectableNode> list = new ArrayList<>();
        collectSelectableNodes(chainJson).forEach((nodeId, node) -> list.add(new SelectableNode(
            nodeId, node.getName(), FlowSignMode.from(node.getSignMode()) != FlowSignMode.SINGLE)));
        return list;
    }
}
