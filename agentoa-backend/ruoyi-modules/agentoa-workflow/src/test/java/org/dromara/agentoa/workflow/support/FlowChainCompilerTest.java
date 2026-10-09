package org.dromara.agentoa.workflow.support;

import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.domain.chain.FlowCondition;
import org.dromara.agentoa.workflow.service.support.ConditionVariableResolver;
import org.dromara.agentoa.workflow.service.support.FlowChainCompiler;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 核心：结构化审批链 → 受控 BPMN 编译。产物必须始终通过 BpmnTemplateValidator 门禁。
 */
class FlowChainCompilerTest {

    private static FlowChainNode approve(String id, String name, String rule, String signMode) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_APPROVE);
        node.setName(name);
        node.setAssigneeRule(rule);
        node.setSignMode(signMode);
        return node;
    }

    private static FlowChainNode cc(String id, String name, String rule) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_CC);
        node.setName(name);
        node.setAssigneeRule(rule);
        return node;
    }

    private static FlowCondition condition(String field, String op, Number value) {
        FlowCondition c = new FlowCondition();
        c.setField(field);
        c.setOp(op);
        c.setValue(value);
        return c;
    }

    /** 钉钉式：上级审批 → 金额>5万加总经理 → 财务会签 → 抄送人事 */
    private static FlowChainConfig dingTalkChain() {
        FlowChainNode branch = new FlowChainNode();
        branch.setId("amountBranch");
        branch.setType(FlowChainNode.TYPE_BRANCH);
        branch.setName("金额分支");

        FlowChainNode.FlowBranch big = new FlowChainNode.FlowBranch();
        big.setId("branchBig");
        big.setName("金额>5万");
        big.setCondition(condition("amount", "gt", 50000));
        big.setNodes(List.of(approve("gmApprove", "总经理审批", "ROLE:gm", "SINGLE")));

        FlowChainNode.FlowBranch other = new FlowChainNode.FlowBranch();
        other.setId("branchOther");
        other.setName("其他");
        other.setCondition(null);
        other.setNodes(List.of());

        branch.setBranches(List.of(big, other));

        FlowChainConfig config = new FlowChainConfig();
        config.setChainVersion(1);
        config.setNodes(List.of(
            approve("leaderApprove", "直属上级审批", "LEADER", "SINGLE"),
            branch,
            approve("financeApprove", "财务会签", "ROLE:finance", "COUNTERSIGN"),
            cc("ccHr", "抄送人事", "ROLE:hr")));
        return config;
    }

    @Test
    void compilesDingTalkChainIntoControlledBpmn() {
        FlowChainCompiler.CompiledFlow compiled =
            FlowChainCompiler.compile(dingTalkChain(), "expenseClaim", "费用报销");
        String xml = compiled.bpmnXml();

        assertThat(xml).contains("<process id=\"expenseClaim\"");
        assertThat(xml).contains("<userTask id=\"leaderApprove\"");
        assertThat(xml).contains("<userTask id=\"gmApprove\"");
        assertThat(xml).contains("<userTask id=\"financeApprove\"");
        assertThat(xml).contains("<exclusiveGateway id=\"amountBranch_split\"");
        assertThat(xml).contains("<exclusiveGateway id=\"amountBranch_join\"");
        assertThat(xml).contains("${amount_gt_50000}");
        assertThat(xml).contains("<multiInstanceLoopCharacteristics isSequential=\"false\"");
        assertThat(xml).contains("${nrOfCompletedInstances == nrOfInstances}");
        assertThat(xml).contains("<bpmndi:BPMNDiagram");
        // 抄送不进 BPMN 图，但要产出抄送规则
        assertThat(xml).doesNotContain("ccHr");
        assertThat(compiled.ccRules()).hasSize(1);
        assertThat(compiled.ccRules().get(0).rule()).isEqualTo("ROLE:hr");
        assertThat(compiled.validationSummary()).contains("userTask:");
    }

    @Test
    void eitherSignUsesAnyCompletionCondition() {
        FlowChainConfig config = new FlowChainConfig();
        config.setNodes(List.of(approve("n1", "或签", "ROLE:gm", "EITHERSIGN")));
        String xml = FlowChainCompiler.compile(config, "p1", "或签流程").bpmnXml();
        assertThat(xml).contains("${nrOfCompletedInstances >= 1}");
    }

    @Test
    void rejectsScriptInjectionThroughNodeFields() {
        // 节点 ID / 规则 / 名称全部走白名单与 ID 正则，无法注入 XML 片段
        FlowChainConfig config = new FlowChainConfig();
        config.setNodes(List.of(approve("n1<script>", "x", "SELF", "SINGLE")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("节点 ID 非法");

        FlowChainConfig ruleInjection = new FlowChainConfig();
        ruleInjection.setNodes(List.of(approve("n1", "x",
            "SELF</flowable:string></flowable:field></flowable:taskListener><scriptTask/>", "SINGLE")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(ruleInjection, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("办理人规则");
    }

    @Test
    void rejectsUnknownRoleAndExpressionConditions() {
        FlowChainConfig badRole = new FlowChainConfig();
        badRole.setNodes(List.of(approve("n1", "x", "ROLE:superadmin", "SINGLE")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(badRole, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("角色不在白名单");

        FlowChainConfig badField = new FlowChainConfig();
        FlowChainNode branch = new FlowChainNode();
        branch.setId("b");
        branch.setType(FlowChainNode.TYPE_BRANCH);
        branch.setName("b");
        FlowChainNode.FlowBranch only = new FlowChainNode.FlowBranch();
        only.setId("b1");
        only.setCondition(condition("runtime", "gt", 1));
        only.setNodes(List.of());
        branch.setBranches(List.of(only));
        badField.setNodes(List.of(branch));
        assertThatThrownBy(() -> FlowChainCompiler.compile(badField, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件字段不在白名单");

        FlowChainConfig badOp = new FlowChainConfig();
        FlowChainNode branch2 = new FlowChainNode();
        branch2.setId("b");
        branch2.setType(FlowChainNode.TYPE_BRANCH);
        branch2.setName("b");
        FlowChainNode.FlowBranch only2 = new FlowChainNode.FlowBranch();
        only2.setId("b1");
        only2.setCondition(condition("amount", "regex", 1));
        only2.setNodes(List.of());
        branch2.setBranches(List.of(only2));
        badOp.setNodes(List.of(branch2));
        assertThatThrownBy(() -> FlowChainCompiler.compile(badOp, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件算子不在白名单");
    }

    @Test
    void rejectsFractionalAndOversizedThresholds() {
        FlowChainConfig config = new FlowChainConfig();
        FlowChainNode branch = new FlowChainNode();
        branch.setId("b");
        branch.setType(FlowChainNode.TYPE_BRANCH);
        branch.setName("b");
        FlowChainNode.FlowBranch only = new FlowChainNode.FlowBranch();
        only.setId("b1");
        only.setCondition(condition("amount", "gt", 12.5));
        only.setNodes(List.of());
        branch.setBranches(List.of(only));
        config.setNodes(List.of(branch));
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件阈值");
    }

    @Test
    void rejectsDuplicateIdsAndDeepNesting() {
        FlowChainConfig dup = new FlowChainConfig();
        dup.setNodes(List.of(
            approve("same", "a", "SELF", "SINGLE"),
            approve("same", "b", "SELF", "SINGLE")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(dup, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 重复");

        FlowChainConfig deep = new FlowChainConfig();
        deep.setNodes(List.of(nest(4)));
        assertThatThrownBy(() -> FlowChainCompiler.compile(deep, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("嵌套过深");
    }

    private static FlowChainNode nest(int depth) {
        FlowChainNode branch = new FlowChainNode();
        branch.setId("d" + depth);
        branch.setType(FlowChainNode.TYPE_BRANCH);
        branch.setName("d" + depth);
        FlowChainNode.FlowBranch only = new FlowChainNode.FlowBranch();
        only.setId("db" + depth);
        only.setNodes(depth <= 1 ? List.of() : List.of(nest(depth - 1)));
        branch.setBranches(List.of(only));
        return branch;
    }

    @Test
    void rejectsMultiInstanceOnSinglePersonRule() {
        FlowChainConfig config = new FlowChainConfig();
        config.setNodes(List.of(approve("n1", "x", "LEADER", "COUNTERSIGN")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("集合型规则");
    }

    @Test
    void conditionVariablesAreEvaluatedFromAmount() {
        assertThat(ConditionVariableResolver.evaluate(java.math.BigDecimal.valueOf(50000), "amount_gt_50000")).isFalse();
        assertThat(ConditionVariableResolver.evaluate(java.math.BigDecimal.valueOf(50001), "amount_gt_50000")).isTrue();
        assertThat(ConditionVariableResolver.evaluate(java.math.BigDecimal.valueOf(50000), "amount_le_50000")).isTrue();
    }

    @Test
    void conditionVariableResolverFailsFastWithoutAmount() {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        String xml = "<x><conditionExpression>${amount_gt_50000}</conditionExpression></x>";
        assertThatThrownBy(() -> ConditionVariableResolver.enrich(variables, xml))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("amount");
    }

    @Test
    void conditionVariableResolverKeepsPrecomputedVariables() {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("amount_le_1000", true);
        String xml = "<x><conditionExpression>${amount_le_1000}</conditionExpression></x>";
        ConditionVariableResolver.enrich(variables, xml);
        assertThat(variables.get("amount_le_1000")).isEqualTo(true);
        assertThat(variables).doesNotContainKey("amount");
    }

    // ------------------------------------------------------------------ USER_SELECT（发起人自选）

    @Test
    void userSelectCompilesForSingleAndCountersign() {
        FlowChainConfig single = new FlowChainConfig();
        single.setNodes(List.of(approve("n1", "自选审批", "USER_SELECT", "SINGLE")));
        String xml = FlowChainCompiler.compile(single, "p1", "自选").bpmnXml();
        assertThat(xml).contains("USER_SELECT");
        assertThat(xml).doesNotContain("multiInstanceLoopCharacteristics");

        FlowChainConfig countersign = new FlowChainConfig();
        countersign.setNodes(List.of(approve("n2", "自选会签", "USER_SELECT", "COUNTERSIGN")));
        String mi = FlowChainCompiler.compile(countersign, "p1", "自选会签").bpmnXml();
        assertThat(mi).contains("flowable:collection=\"${mi_n2}\"");
        assertThat(mi).contains("USER_SELECT");

        FlowChainConfig eitherSign = new FlowChainConfig();
        eitherSign.setNodes(List.of(approve("n3", "自选或签", "USER_SELECT", "EITHERSIGN")));
        assertThat(FlowChainCompiler.compile(eitherSign, "p1", "自选或签").bpmnXml())
            .contains("flowable:collection=\"${mi_n3}\"");
    }

    @Test
    void rejectsUserSelectOnCcNode() {
        FlowChainConfig config = new FlowChainConfig();
        config.setNodes(List.of(cc("n1", "抄送", "USER_SELECT")));
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("抄送节点不支持发起人自选");
    }

    @Test
    void roleExistenceHookReplacesFixedWhitelist() {
        FlowChainConfig config = new FlowChainConfig();
        config.setNodes(List.of(approve("n1", "x", "ROLE:custom_role", "SINGLE")));
        // 未提供钩子时退回内置角色白名单
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("白名单");
        // 角色存在性钩子放行自定义角色
        assertThat(FlowChainCompiler.compile(config, "p1", "x", key -> "custom_role".equals(key)).bpmnXml())
            .contains("ROLE:custom_role");
        // 钩子拒绝时仍然失败（角色不存在）
        assertThatThrownBy(() -> FlowChainCompiler.compile(config, "p1", "x", key -> false))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("角色不存在");
    }
}
