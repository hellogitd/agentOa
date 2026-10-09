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
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.support.WfTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M6 · 流程定义管理运行时：条件分支路由、删除保护、自定义定义生命周期、通用 OA 表单直接发起。
 */
class WorkflowDefinitionH2Test {

    @BeforeAll
    static void boot() {
        WfTestEnvironment.bootstrap();
        WfTestEnvironment.registerTemplates();
    }

    /** 自定义定义/表单只属于本测试类，跑完整体回收，避免污染其它测试类的计数断言 */
    @org.junit.jupiter.api.AfterAll
    static void cleanupCustomObjects() {
        WfTestEnvironment.logout();
        WfTestEnvironment.clearFlowData();
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition_version WHERE definition_id IN "
                + "(SELECT id FROM oa_flow_definition WHERE business_type LIKE 'custom%' OR business_type = 'lifecycle')");
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition WHERE business_type LIKE 'custom%' OR business_type = 'lifecycle'");
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_form_version WHERE form_key LIKE 'custom%' OR form_key LIKE 'lifeCycle%'");
    }

    @BeforeEach
    void resetWorld() {
        WfTestEnvironment.logout();
        WfTestEnvironment.clearFlowData();
        // 自定义定义只属于本测试类，清掉避免污染其它测试类的计数断言
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition_version WHERE definition_id IN "
                + "(SELECT id FROM oa_flow_definition WHERE business_type LIKE 'custom%' OR business_type = 'lifecycle')");
        WfTestEnvironment.jdbc().update(
            "DELETE FROM oa_flow_definition WHERE business_type LIKE 'custom%' OR business_type = 'lifecycle'");
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee_history");
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user_role");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_dept");
        WfTestEnvironment.jdbc().update(
            "INSERT INTO sys_dept(dept_id, parent_id, ancestors, dept_name, leader) VALUES(10, 0, '0', 'test', ?)",
            WfTestEnvironment.USER_LEADER);
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, "leader");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, "employee");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, "hr");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_FINANCE, WfTestEnvironment.DEPT_A, "finance");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_CASHIER, WfTestEnvironment.DEPT_A, "cashier");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_DIRECTOR, WfTestEnvironment.DEPT_A, "director");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_ADMIN, WfTestEnvironment.DEPT_A, "admin");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_GM, WfTestEnvironment.DEPT_A, "gm");
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_HR, 20L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_LEADER, 21L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_EMPLOYEE, 22L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_FINANCE, 23L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_CASHIER, 24L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_DIRECTOR, 25L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_ADMIN, 26L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_GM, 27L);
        insertEmployee(1000L, WfTestEnvironment.USER_EMPLOYEE);
    }

    private void insertEmployee(Long id, Long userId) {
        WfTestEnvironment.jdbc().update(
            "INSERT INTO oa_employee(id, user_id, employee_no, name, dept_id, direct_leader_id, status)"
                + " VALUES(?,?,?,?,?,?,?)",
            id, userId, "E" + id, "emp" + id, WfTestEnvironment.DEPT_A, WfTestEnvironment.USER_LEADER, "ACTIVE");
    }

    // ------------------------------------------------------------------ 条件分支运行时

    @Test
    void amountOver50kRoutesThroughGeneralManager() {
        InstanceStartVo start = launchLoan("80000.00");
        List<String> chain = drain(start);
        assertThat(chain).containsExactly("主管审批", "财务审核", "总经理审批", "出纳付款");
    }

    @Test
    void amountAtOrBelow50kSkipsGeneralManager() {
        InstanceStartVo start = launchLoan("30000.00");
        List<String> chain = drain(start);
        assertThat(chain).containsExactly("主管审批", "财务审核", "出纳付款");
    }

    @Test
    void reimburseKeepsThreeTierAmountBranches() {
        // reimburse 有专用承接器（ReimburseRequestServiceImpl），走真实业务单据路径回归金额分级分支：
        // <=1000 财务审核后结束；1000<x<=5000 加出纳付款；>5000 加总监审批
        List<String> small = drain(submitReimburse("800.00"));
        assertThat(small).containsExactly("主管审批", "财务审核");
        List<String> mid = drain(submitReimburse("3000.00"));
        assertThat(mid).containsExactly("主管审批", "财务审核", "出纳付款");
        List<String> big = drain(submitReimburse("8000.00"));
        assertThat(big).containsExactly("主管审批", "财务审核", "总监审批", "出纳付款");
    }

    // ------------------------------------------------------------------ 通用 OA 表单直接发起

    @Test
    void genericFormLaunchStartsFlowWithoutBusinessTable() {
        Long definitionId = definitionIdOf("seal");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setTitle("用印申请-合同盖章");
        bo.setFormData("{\"sealType\":\"contract\",\"copies\":2,\"reason\":\"对外签约\"}");
        BusinessRequestVo vo = WfTestEnvironment.genericService.launch(bo);

        assertThat(vo.getStatus()).isEqualTo(2);
        assertThat(vo.getFlowInstanceId()).isNotNull();
        assertThat(vo.getFormData()).contains("sealType");

        List<String> chain = drain(WfTestEnvironment.instanceService.selectStartResult(vo.getFlowInstanceId()));
        assertThat(chain).containsExactly("主管审批", "行政审批", "总经理审批");
    }

    @Test
    void genericFormAmountFieldDrivesConditionalBranch() {
        Long definitionId = definitionIdOf("loan");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setFormData("{\"amount\":80000,\"purpose\":\"项目垫资\",\"repayDate\":\"2026-12-31\"}");
        BusinessRequestVo vo = WfTestEnvironment.genericService.launch(bo);
        List<String> chain = drain(WfTestEnvironment.instanceService.selectStartResult(vo.getFlowInstanceId()));
        assertThat(chain).contains("总经理审批");
    }

    @Test
    void genericFormRejectsNonJsonPayload() {
        Long definitionId = definitionIdOf("seal");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setFormData("DROP TABLE oa_flow_definition");
        assertThatThrownBy(() -> WfTestEnvironment.genericService.launch(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("JSON");
    }

    // ------------------------------------------------------------------ 删除保护

    @Test
    void deleteProtectionBlocksBuiltInTemplates() {
        Long builtinId = definitionIdOf("leave");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.templateService.deleteDefinition(builtinId))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("内置模板");
    }

    @Test
    void deleteProtectionBlocksDefinitionsWithInstances() {
        Long customId = createCustomDefinition("customUsed");
        InstanceStartVo start = startCustom(customId);
        assertThat(start.getInstanceId()).isNotNull();

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.templateService.deleteDefinition(customId))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("实例");
    }

    @Test
    void deleteRemovesUnusedCustomDefinition() {
        Long customId = createCustomDefinition("customUnused");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        WfTestEnvironment.templateService.deleteDefinition(customId);
        assertThatThrownBy(() -> WfTestEnvironment.templateService.selectDefinition(customId))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("流程定义不存在");
    }

    @Test
    void deleteUnknownDefinitionReturns404() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.templateService.deleteDefinition(999999L))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
    }

    // ------------------------------------------------------------------ 自定义定义生命周期

    @Test
    void customDefinitionCreateEditPublishDisableEnable() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        DefinitionBo bo = customBo("lifeCycle");
        DefinitionVo created = WfTestEnvironment.templateService.createDefinition(bo);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(created.getChain()).contains("chainVersion");
        assertThat(created.getForm()).isNotNull();
        assertThat(created.getBuiltin()).isFalse();
        assertThat(created.getVersions()).hasSize(1);

        // 更新会产出新版本（版本发布后不可变）
        DefinitionBo edit = customBo("lifeCycle");
        edit.setProcessKey(null);
        edit.getChain().getNodes().add(ccNode("ccHr", "抄送人事", "ROLE:hr"));
        DefinitionVo updated = WfTestEnvironment.templateService.updateDefinition(created.getId(), edit);
        assertThat(updated.getVersions()).hasSize(2);

        // 发布 v1
        DefinitionVo published = WfTestEnvironment.templateService.publish(created.getId(), 1);
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getVersions().get(0).getStatus()).isEqualTo("PUBLISHED");

        // 停用 → 不可发起
        DefinitionStatusBo disabled = new DefinitionStatusBo();
        disabled.setStatus("RETIRED");
        assertThat(WfTestEnvironment.templateService.changeStatus(created.getId(), disabled).getStatus())
            .isEqualTo("RETIRED");
        assertThatThrownBy(() -> startCustom(created.getId()))
            .isInstanceOf(ServiceException.class);

        // 一键启用内置模板不影响自定义；单独启用自定义定义恢复可用
        DefinitionStatusBo enabled = new DefinitionStatusBo();
        enabled.setStatus("PUBLISHED");
        DefinitionVo reEnabled = WfTestEnvironment.templateService.changeStatus(created.getId(), enabled);
        assertThat(reEnabled.getStatus()).isEqualTo("PUBLISHED");
        assertThat(startCustom(created.getId()).getInstanceId()).isNotNull();
    }

    @Test
    void oneClickEnableBuiltinTemplates() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        DefinitionStatusBo disabled = new DefinitionStatusBo();
        disabled.setStatus("RETIRED");
        WfTestEnvironment.templateService.changeStatus(definitionIdOf("travel"), disabled);

        int enabled = WfTestEnvironment.templateService.enableBuiltinTemplates();
        assertThat(enabled).isEqualTo(1);
        assertThat(WfTestEnvironment.templateService.selectDefinition(definitionIdOf("travel")).getStatus())
            .isEqualTo("PUBLISHED");
        // 再跑一次是幂等的
        assertThat(WfTestEnvironment.templateService.enableBuiltinTemplates()).isZero();
    }

    @Test
    void builtinCatalogMatchesExpectedBuckets() {
        var all = WfTestEnvironment.templateService.selectDefinitions();
        long hr = all.stream().filter(d -> "100".equals(String.valueOf(d.getCategoryId()))).count();
        long finance = all.stream().filter(d -> "101".equals(String.valueOf(d.getCategoryId()))).count();
        long attendance = all.stream().filter(d -> "102".equals(String.valueOf(d.getCategoryId()))).count();
        long other = all.stream().filter(d -> "103".equals(String.valueOf(d.getCategoryId()))).count();
        long admin = all.stream().filter(d -> "104".equals(String.valueOf(d.getCategoryId()))).count();
        assertThat(hr).isEqualTo(10);
        assertThat(finance).isEqualTo(5);
        assertThat(attendance + admin).isEqualTo(7);
        assertThat(other).isEqualTo(4);
    }

    @Test
    void launchCatalogOnlyExposesPublishedDefinitionsWithActiveCategories() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        var catalog = WfTestEnvironment.templateService.selectLaunchCatalog();
        assertThat(catalog.getCategories()).extracting("name")
            .contains("人事流程", "财务流程", "考勤流程", "其他", "行政办公");
        assertThat(catalog.getDefinitions()).isNotEmpty();
        assertThat(catalog.getDefinitions()).allSatisfy(definition -> {
            assertThat(definition.getForm()).isNotNull();
            assertThat(definition.getForm().getSchema()).isNotBlank();
            assertThat(definition.getCategoryName()).isNotBlank();
        });

        // 停用定义：从发起目录消失，管理端全量目录仍可见
        DefinitionStatusBo disabled = new DefinitionStatusBo();
        disabled.setStatus("RETIRED");
        WfTestEnvironment.templateService.changeStatus(definitionIdOf("travel"), disabled);
        assertThat(WfTestEnvironment.templateService.selectLaunchCatalog().getDefinitions())
            .noneMatch(definition -> "travel".equals(definition.getProcessKey()));
        assertThat(WfTestEnvironment.templateService.selectDefinitions())
            .anyMatch(definition -> "travel".equals(definition.getProcessKey()));
        assertThatThrownBy(() -> WfTestEnvironment.templateService.selectLaunchableDefinition(definitionIdOf("travel")))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
        assertThat(WfTestEnvironment.templateService.selectLaunchableDefinition(definitionIdOf("leave")).getForm())
            .isNotNull();

        // 停用分类：分类与其下定义一并从发起目录消失
        WfTestEnvironment.jdbc().update("UPDATE oa_flow_category SET status = '1' WHERE id = 104");
        try {
            var withoutAdmin = WfTestEnvironment.templateService.selectLaunchCatalog();
            assertThat(withoutAdmin.getCategories()).noneMatch(category -> "ADMIN".equals(category.getCode()));
            assertThat(withoutAdmin.getDefinitions())
                .noneMatch(definition -> Long.valueOf(104L).equals(definition.getCategoryId()));
            assertThatThrownBy(() -> WfTestEnvironment.templateService.selectLaunchableDefinition(definitionIdOf("seal")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
        } finally {
            WfTestEnvironment.jdbc().update("UPDATE oa_flow_category SET status = '0' WHERE id = 104");
            DefinitionStatusBo enabled = new DefinitionStatusBo();
            enabled.setStatus("PUBLISHED");
            WfTestEnvironment.templateService.changeStatus(definitionIdOf("travel"), enabled);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Long definitionIdOf(String processKey) {
        return WfTestEnvironment.templateService.selectDefinitions().stream()
            .filter(d -> processKey.equals(d.getProcessKey()))
            .findFirst()
            .orElseThrow()
            .getId();
    }

    private InstanceStartVo launchLoan(String amount) {
        return launchGeneric("loan", "{\"amount\":" + amount + ",\"purpose\":\"test\",\"repayDate\":\"2026-12-31\"}");
    }

    private InstanceStartVo submitReimburse(String amount) {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo bo =
            new org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo();
        bo.setReimburseType("expense");
        bo.setPayMethod("BANK_TRANSFER");
        org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo.ReimburseDetailBo detail =
            new org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo.ReimburseDetailBo();
        detail.setExpenseType("办公用品");
        detail.setOccurDate(java.time.LocalDate.now());
        detail.setAmount(amount);
        detail.setDescription("test");
        bo.setDetails(List.of(detail));
        BusinessRequestVo draft = WfTestEnvironment.reimburseService.createDraft(bo);
        return WfTestEnvironment.reimburseService.submit(draft.getId(), draft.getLockVersion());
    }

    private InstanceStartVo launchGeneric(String processKey, String formData) {
        Long definitionId = definitionIdOf(processKey);
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setFormData(formData);
        BusinessRequestVo vo = WfTestEnvironment.genericService.launch(bo);
        return WfTestEnvironment.instanceService.selectStartResult(vo.getFlowInstanceId());
    }

    /** 依次同意直到流程结束，返回办理过的节点名称（按顺序） */
    private List<String> drain(InstanceStartVo start) {
        List<String> names = new java.util.ArrayList<>();
        int guard = 0;
        while (guard++ < 20) {
            List<TaskVo> current = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
            if (current.isEmpty()) {
                break;
            }
            TaskVo task = current.get(0);
            names.add(task.getTaskName());
            WfTestEnvironment.loginAs(assigneeOf(task.getTaskName()), WfTestEnvironment.DEPT_A, Set.of());
            org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo complete =
                new org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo();
            complete.setAction("agree");
            complete.setComment("ok");
            WfTestEnvironment.taskService.complete(task.getTaskId(), complete);
        }
        return names;
    }

    private Long assigneeOf(String taskName) {
        return switch (taskName) {
            case "总经理审批" -> WfTestEnvironment.USER_GM;
            case "出纳付款" -> WfTestEnvironment.USER_CASHIER;
            case "总监审批" -> WfTestEnvironment.USER_DIRECTOR;
            case "财务审核", "财务复核", "财务处理" -> WfTestEnvironment.USER_FINANCE;
            case "行政审批", "行政发放", "行政派车", "行政受理" -> WfTestEnvironment.USER_ADMIN;
            case "HR备案", "人事办理", "人事审批", "人事复核", "HR面谈", "HR复核" -> WfTestEnvironment.USER_HR;
            default -> WfTestEnvironment.USER_LEADER;
        };
    }

    private DefinitionBo customBo(String processKey) {
        DefinitionBo bo = new DefinitionBo();
        bo.setProcessKey(processKey);
        bo.setProcessName("自定义流程-" + processKey);
        bo.setCategoryId(103L);
        // businessType 标识只允许小写字母/数字/下划线
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
        form.setFormName("自定义表单-" + processKey);
        form.setSchema(schema);
        bo.setForm(form);

        FlowChainConfig chain = new FlowChainConfig();
        chain.setChainVersion(1);
        chain.setNodes(new java.util.ArrayList<>(List.of(approveNode("leaderApprove", "主管审批", "LEADER"))));
        bo.setChain(chain);
        return bo;
    }

    private FlowChainNode approveNode(String id, String name, String rule) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_APPROVE);
        node.setName(name);
        node.setAssigneeRule(rule);
        node.setSignMode("SINGLE");
        return node;
    }

    private FlowChainNode ccNode(String id, String name, String rule) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_CC);
        node.setName(name);
        node.setAssigneeRule(rule);
        return node;
    }

    private Long createCustomDefinition(String processKey) {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        DefinitionVo vo = WfTestEnvironment.templateService.createDefinition(customBo(processKey));
        DefinitionStatusBo enabled = new DefinitionStatusBo();
        enabled.setStatus("PUBLISHED");
        WfTestEnvironment.templateService.changeStatus(vo.getId(), enabled);
        return vo.getId();
    }

    private InstanceStartVo startCustom(Long definitionId) {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        GenericRequestBo bo = new GenericRequestBo();
        bo.setDefinitionId(definitionId);
        bo.setFormData("{\"amount\":100}");
        BusinessRequestVo vo = WfTestEnvironment.genericService.launch(bo);
        return WfTestEnvironment.instanceService.selectStartResult(vo.getFlowInstanceId());
    }
}
