package org.dromara.agentoa.workflow;

import org.dromara.agentoa.workflow.domain.bo.DefinitionBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionStatusBo;
import org.dromara.agentoa.workflow.domain.bo.FormDesignBo;
import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.agentoa.workflow.domain.bo.GenericRequestBo;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.DefinitionVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.support.WfTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发起人自选审批人（USER_SELECT）：发起选人 → 任务 assignee 归属、会签集合解析、
 * 伪造节点 ID / 缺失选择 / 非法 userId 的确定性 400 回滚。
 */
class UserSelectH2Test {

    private static final String SINGLE_NODE = "pickApprover";
    private static final String COUNTERSIGN_NODE = "pickPanel";

    @BeforeAll
    static void boot() {
        WfTestEnvironment.bootstrap();
        WfTestEnvironment.registerTemplates();
    }

    /** 自建定义不污染其它用例的模板计数（如 templateRegistrationPublishesSixTemplates） */
    @AfterAll
    static void cleanupCustomDefinitions() {
        WfTestEnvironment.logout();
        WfTestEnvironment.clearFlowData();
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition_version WHERE definition_id IN "
                + "(SELECT id FROM oa_flow_definition WHERE business_type LIKE 'usel%')");
        WfTestEnvironment.jdbc().update("DELETE FROM oa_flow_definition WHERE business_type LIKE 'usel%'");
        WfTestEnvironment.jdbc().update("DELETE FROM oa_flow_form_version WHERE form_key LIKE 'usel%'");
    }

    @BeforeEach
    void resetWorld() {
        WfTestEnvironment.logout();
        WfTestEnvironment.clearFlowData();
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition_version WHERE definition_id IN "
                + "(SELECT id FROM oa_flow_definition WHERE business_type LIKE 'usel%')");
        WfTestEnvironment.jdbc().update("DELETE FROM oa_flow_definition WHERE business_type LIKE 'usel%'");
        WfTestEnvironment.jdbc().update("DELETE FROM oa_flow_form_version WHERE form_key LIKE 'usel%'");
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee_history");
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user_role");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_dept");
        WfTestEnvironment.jdbc().update(
            "INSERT INTO sys_dept(dept_id, parent_id, ancestors, dept_name, leader) VALUES(10, 0, '0', 'test', ?)",
            WfTestEnvironment.USER_LEADER);
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, "主管");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, "员工");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, "同事");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, "人事");
    }

    // ------------------------------------------------------------------ 发起选人 → 任务归属

    @Test
    void userSelectSingleNodeAssignsChosenApprover() {
        Long definitionId = createDefinition("uselSingle", List.of(userSelectNode(SINGLE_NODE, "自选审批", "SINGLE")));
        InstanceStartVo start = launch(definitionId, Map.of(SINGLE_NODE, WfTestEnvironment.USER_OTHER));
        assertThat(start.getCurrentTasks()).hasSize(1);
        assertThat(start.getCurrentTasks().get(0).getAssigneeId()).isEqualTo(WfTestEnvironment.USER_OTHER);
    }

    @Test
    void userSelectCountersignBuildsMultiInstanceFromSelection() {
        Long definitionId = createDefinition("uselPanel",
            List.of(userSelectNode(COUNTERSIGN_NODE, "自选会签", "COUNTERSIGN")));
        InstanceStartVo start = launch(definitionId,
            Map.of(COUNTERSIGN_NODE, List.of(WfTestEnvironment.USER_OTHER, WfTestEnvironment.USER_HR)));
        assertThat(start.getCurrentTasks()).hasSize(2);
        assertThat(start.getCurrentTasks())
            .extracting(InstanceStartVo.CurrentTaskVo::getAssigneeId)
            .containsExactlyInAnyOrder(WfTestEnvironment.USER_OTHER, WfTestEnvironment.USER_HR);
    }

    @Test
    void singleNodeRejectsMultipleSelections() {
        Long definitionId = createDefinition("uselSingle2", List.of(userSelectNode(SINGLE_NODE, "自选审批", "SINGLE")));
        assertThatThrownBy(() -> launch(definitionId,
            Map.of(SINGLE_NODE, List.of(WfTestEnvironment.USER_OTHER, WfTestEnvironment.USER_HR))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("只能选择 1 名审批人");
    }

    // ------------------------------------------------------------------ 确定性 400 回滚

    @Test
    void missingSelectionRollsBackWith400() {
        Long definitionId = createDefinition("uselMissing", List.of(userSelectNode(SINGLE_NODE, "自选审批", "SINGLE")));
        long before = WfTestEnvironment.countInstances();
        assertThatThrownBy(() -> launch(definitionId, Map.of()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未选择审批人");
        assertThat(WfTestEnvironment.countInstances()).isEqualTo(before);
        assertThat(WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM oa_flow_business_ref", Long.class)).isEqualTo(0L);
        assertThat(WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM sys_outbox", Long.class)).isEqualTo(0L);
    }

    @Test
    void forgedNodeIdIsRejected() {
        Long definitionId = createDefinition("uselForged", List.of(userSelectNode(SINGLE_NODE, "自选审批", "SINGLE")));
        assertThatThrownBy(() -> launch(definitionId,
            Map.of("initiatorUserId", WfTestEnvironment.USER_OTHER)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不可自选");
        // 伪造合法形态但不在链上的节点 ID 同样拒绝
        assertThatThrownBy(() -> launch(definitionId,
            Map.of("userSelect_" + SINGLE_NODE, WfTestEnvironment.USER_OTHER)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不可自选");
    }

    @Test
    void unknownOrDisabledUserIsRejected() {
        Long definitionId = createDefinition("uselBadUser", List.of(userSelectNode(SINGLE_NODE, "自选审批", "SINGLE")));
        assertThatThrownBy(() -> launch(definitionId, Map.of(SINGLE_NODE, 999_999L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在或已停用");
        WfTestEnvironment.jdbc().update("UPDATE sys_user SET status = '1' WHERE user_id = ?",
            WfTestEnvironment.USER_OTHER);
        assertThatThrownBy(() -> launch(definitionId, Map.of(SINGLE_NODE, WfTestEnvironment.USER_OTHER)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在或已停用");
    }

    // ------------------------------------------------------------------ 发起端回显

    @Test
    void launchDefinitionExposesSelectableNodesOnly() {
        Long definitionId = createDefinition("uselEcho", List.of(
            userSelectNode(SINGLE_NODE, "自选审批", "SINGLE"),
            userSelectNode(COUNTERSIGN_NODE, "自选会签", "EITHERSIGN")));
        var vo = WfTestEnvironment.templateService.selectLaunchableDefinition(definitionId);
        assertThat(vo.getSelectableNodes()).hasSize(2);
        assertThat(vo.getSelectableNodes()).anySatisfy(node -> {
            assertThat(node.getNodeId()).isEqualTo(SINGLE_NODE);
            assertThat(node.getName()).isEqualTo("自选审批");
            assertThat(node.getMultiple()).isFalse();
        });
        assertThat(vo.getSelectableNodes()).anySatisfy(node -> {
            assertThat(node.getNodeId()).isEqualTo(COUNTERSIGN_NODE);
            assertThat(node.getMultiple()).isTrue();
        });
    }

    // ------------------------------------------------------------------ helpers

    private FlowChainNode userSelectNode(String id, String name, String signMode) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_APPROVE);
        node.setName(name);
        node.setAssigneeRule("USER_SELECT");
        node.setSignMode(signMode);
        return node;
    }

    private Long createDefinition(String processKey, List<FlowChainNode> nodes) {
        DefinitionBo bo = new DefinitionBo();
        bo.setProcessKey(processKey);
        bo.setProcessName("自选流程-" + processKey);
        bo.setCategoryId(103L);
        bo.setBusinessType(processKey.toLowerCase(java.util.Locale.ROOT));
        bo.setSort(99);

        FormSchemaBo.FormFieldBo amount = new FormSchemaBo.FormFieldBo();
        amount.setKey("amount");
        amount.setLabel("金额");
        amount.setType("number");
        amount.setRequired(true);
        FormSchemaBo schema = new FormSchemaBo();
        schema.setFields(List.of(amount));
        FormDesignBo form = new FormDesignBo();
        form.setFormKey(processKey + "Form");
        form.setFormName("自选表单-" + processKey);
        form.setSchema(schema);
        bo.setForm(form);

        FlowChainConfig chain = new FlowChainConfig();
        chain.setChainVersion(1);
        chain.setNodes(new ArrayList<>(nodes));
        bo.setChain(chain);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        DefinitionVo vo = WfTestEnvironment.templateService.createDefinition(bo);
        DefinitionStatusBo enabled = new DefinitionStatusBo();
        enabled.setStatus("PUBLISHED");
        WfTestEnvironment.templateService.changeStatus(vo.getId(), enabled);
        return vo.getId();
    }

    private InstanceStartVo launch(Long definitionId, Map<String, Object> assigneeSelections) {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setTitle("自选审批测试");
        bo.setFormData("{\"amount\":100}");
        bo.setAssigneeSelections(assigneeSelections);
        // 测试环境无 Spring 事务代理，手动套事务以校验失败整体回滚
        BusinessRequestVo vo = WfTestEnvironment.inTransaction(() -> WfTestEnvironment.genericService.launch(bo));
        return WfTestEnvironment.instanceService.selectStartResult(vo.getFlowInstanceId());
    }
}
