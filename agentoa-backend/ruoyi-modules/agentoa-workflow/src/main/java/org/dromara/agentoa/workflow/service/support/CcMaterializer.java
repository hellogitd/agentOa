package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.agentoa.workflow.assigner.AssigneeResolver;
import org.dromara.agentoa.workflow.domain.OaFlowCc;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.mapper.OaFlowCcMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 审批链抄送节点落地：实例启动时把链配置里的 {@code cc} 节点写成 {@code oa_flow_cc} 抄送记录。
 * <p>
 * 抄送节点不进 BPMN 图（保持元素白名单不扩张），但仍是审批链配置的一等节点；
 * 语义为「发起时抄送」，与 {@code /api/v1/wf/instances/{id}/cc} 手工抄送共用同一张表与同一事件 ID 规则，
 * 因此同一实例同一被抄送人只会有一条记录（重复插入被唯一键吞掉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CcMaterializer {

    private final OaFlowCcMapper ccMapper;
    private final AssigneeResolver assigneeResolver;

    /**
     * 写入链配置里的抄送记录，返回成功投递条数。
     */
    public int materialize(OaFlowInstance instance, String chainJson) {
        List<FlowChainNode> ccNodes = collectCcNodes(chainJson);
        if (ccNodes.isEmpty()) {
            return 0;
        }
        int written = 0;
        int index = 0;
        for (FlowChainNode node : ccNodes) {
            index++;
            List<Long> users;
            try {
                AssigneeResolver.AssigneeSet set = assigneeResolver.resolve(node.getAssigneeRule(),
                    instance.getInitiatorUserId(), instance.getInitiatorDeptId());
                users = set.allUserIds();
            } catch (Exception e) {
                throw new org.dromara.common.core.exception.ServiceException(
                    "抄送人规则解析失败（" + node.getName() + "）: " + e.getMessage(), 400);
            }
            for (Long ccUserId : users) {
                if (ccUserId == null || ccUserId.equals(instance.getInitiatorUserId())) {
                    continue;
                }
                OaFlowCc record = new OaFlowCc();
                record.setInstanceId(instance.getId());
                record.setSenderUserId(instance.getInitiatorUserId());
                record.setCcUserId(ccUserId);
                record.setComment("流程抄送：" + node.getName());
                record.setEventId("FCC-" + instance.getId() + "-" + ccUserId);
                record.setCreateTime(new Date());
                try {
                    ccMapper.insert(record);
                    written++;
                } catch (DuplicateKeyException ignored) {
                    // 同一实例同一被抄送人只保留一条
                }
            }
        }
        return written;
    }

    /** 递归收集链配置里全部 cc 节点（含条件分支内部） */
    public static List<FlowChainNode> collectCcNodes(String chainJson) {
        List<FlowChainNode> result = new ArrayList<>();
        if (chainJson == null || chainJson.isBlank()) {
            return result;
        }
        FlowChainConfig config;
        try {
            config = PlainJson.parse(chainJson, FlowChainConfig.class);
        } catch (Exception e) {
            log.warn("审批链配置解析失败，跳过抄送落地: {}", e.toString());
            return result;
        }
        if (config != null) {
            walk(config.getNodes(), result);
        }
        return result;
    }

    private static void walk(List<FlowChainNode> nodes, List<FlowChainNode> out) {
        if (nodes == null) {
            return;
        }
        for (FlowChainNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (FlowChainNode.TYPE_CC.equals(node.getType())) {
                out.add(node);
            }
            walk(node.getNodes(), out);
            if (node.getBranches() != null) {
                for (FlowChainNode.FlowBranch branch : node.getBranches()) {
                    if (branch != null) {
                        walk(branch.getNodes(), out);
                    }
                }
            }
        }
    }
}
