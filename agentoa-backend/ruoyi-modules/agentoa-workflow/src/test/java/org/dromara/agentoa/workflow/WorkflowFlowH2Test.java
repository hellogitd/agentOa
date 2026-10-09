package org.dromara.agentoa.workflow;

import org.dromara.agentoa.workflow.domain.bo.LeaveRequestBo;
import org.dromara.agentoa.workflow.domain.bo.LifecycleRequestBo;
import org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo;
import org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo;
import org.dromara.agentoa.workflow.domain.bo.TaskTransferBo;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceDetailVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.support.WfTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFlowH2Test {

    @BeforeAll
    static void boot() {
        WfTestEnvironment.bootstrap();
        WfTestEnvironment.registerTemplates();
    }

    @BeforeEach
    void resetWorld() {
        WfTestEnvironment.logout();
        WfTestEnvironment.clearFlowData();
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee_history");
        WfTestEnvironment.jdbc().execute("DELETE FROM oa_employee");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user_role");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_user");
        WfTestEnvironment.jdbc().execute("DELETE FROM sys_dept");
        WfTestEnvironment.jdbc().update(
            "INSERT INTO sys_dept(dept_id, parent_id, ancestors, dept_name, leader) VALUES(10, 0, '0', '测试部', NULL)");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, "主管");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, "员工");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, "同事");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, "人事");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_FINANCE, WfTestEnvironment.DEPT_A, "财务");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_CASHIER, WfTestEnvironment.DEPT_A, "出纳");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_DIRECTOR, WfTestEnvironment.DEPT_A, "总监");
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_HR, 20L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_LEADER, 21L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_EMPLOYEE, 22L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_FINANCE, 23L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_CASHIER, 24L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_DIRECTOR, 25L);
        insertEmployee(1000L, WfTestEnvironment.USER_EMPLOYEE, "PROBATION", WfTestEnvironment.USER_LEADER);
        insertEmployee(1001L, WfTestEnvironment.USER_OTHER, "ACTIVE", WfTestEnvironment.USER_LEADER);
    }

    @Test
    void templateRegistrationPublishesSixTemplates() {
        var definitions = WfTestEnvironment.templateService.selectDefinitions();
        assertThat(definitions).hasSize(26);
        assertThat(definitions).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo("PUBLISHED");
            assertThat(d.getVersions()).hasSize(1);
            assertThat(d.getVersions().get(0).getStatus()).isEqualTo("PUBLISHED");
        });
        assertThat(WfTestEnvironment.templateService.selectForms()).hasSize(26);
        assertThat(WfTestEnvironment.templateService.selectForm("leave").getSchema()).contains("schemaVersion");
    }
    @Test
    void leaveHappyPathApprovesAndWritesHistory() {
        InstanceStartVo start = submitLeave();
        assertThat(start.getCurrentTasks()).hasSize(1);
        String firstTask = start.getCurrentTasks().get(0).getTaskId();
        assertThat(start.getCurrentTasks().get(0).getAssigneeId()).isEqualTo(WfTestEnvironment.USER_LEADER);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords()).hasSize(1);
        agree(firstTask);
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of());
        List<TaskVo> hrTodo = WfTestEnvironment.taskService.selectTodo(newPage()).getRecords();
        assertThat(hrTodo).hasSize(1);
        agree(hrTodo.get(0).getTaskId());

        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId());
        assertThat(detail.getStatus()).isEqualTo(2);
        assertThat(detail.getActions()).hasSize(2);
        assertThat(detail.getActions()).allSatisfy(a -> assertThat(a.getAction()).isEqualTo("agree"));
        assertThat(detail.getFormData()).contains("leaveType");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectDone(newPage()).getRecords()).isEmpty();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectDone(newPage()).getRecords()).hasSize(1);
    }

    @Test
    void rejectEndsInstanceAndNotifiesInitiator() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        TaskCompleteBo reject = new TaskCompleteBo();
        reject.setAction("reject");
        reject.setComment("不同意");
        WfTestEnvironment.taskService.complete(start.getCurrentTasks().get(0).getTaskId(), reject);
        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId());
        assertThat(detail.getStatus()).isEqualTo(3);
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        BusinessRequestVo request = WfTestEnvironment.leaveService.selectRequest(
            detail.getBusinessId());
        assertThat(request.getStatus()).isEqualTo(4);
        Long outbox = WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM sys_outbox WHERE event_type = 'SYSTEM'", Long.class);
        assertThat(outbox).isEqualTo(1L);
    }

    @Test
    void revokeAllowedOnlyForInitiatorBeforeAnyAction() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.revoke(start.getInstanceId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.revoke(start.getInstanceId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(403));

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.instanceService.revoke(start.getInstanceId()).getStatus()).isEqualTo(4);
        BusinessRequestVo request = WfTestEnvironment.leaveService.selectRequest(
            WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getBusinessId());
        assertThat(request.getStatus()).isEqualTo(7);
        assertThat(WfTestEnvironment.countInstances()).isEqualTo(1L);
    }

    @Test
    void revokeAfterAnyActionConflicts() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        agree(start.getCurrentTasks().get(0).getTaskId());
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.revoke(start.getInstanceId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
    }

    @Test
    void transferMovesAssigneeAndStrangersGet403() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        TaskCompleteBo hijack = new TaskCompleteBo();
        hijack.setAction("agree");
        assertThatThrownBy(() -> WfTestEnvironment.taskService.complete(taskId, hijack))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(403));

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        TaskTransferBo transfer = new TaskTransferBo();
        transfer.setTargetUserId(WfTestEnvironment.USER_OTHER);
        transfer.setReason("出差代办");
        WfTestEnvironment.taskService.transfer(taskId, transfer);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        agree(taskId);
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of());
        agree(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords().get(0).getTaskId());
        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId());
        assertThat(detail.getStatus()).isEqualTo(2);
        assertThat(detail.getActions()).anySatisfy(a -> {
            assertThat(a.getAction()).isEqualTo("transfer");
            assertThat(a.getNewAssigneeName()).isEqualTo("同事");
        });
    }

    @Test
    void reimburseBranchesByAmount() {
        assertThat(chainOf(submitReimburse("800.00"))).containsExactly("主管审批", "财务审核");
        WfTestEnvironment.clearFlowData();
        assertThat(chainOf(submitReimburse("3000.00"))).containsExactly("主管审批", "财务审核", "出纳付款");
        WfTestEnvironment.clearFlowData();
        assertThat(chainOf(submitReimburse("8000.00"))).containsExactly("主管审批", "财务审核", "总监审批", "出纳付款");
    }

    @Test
    void idempotencyGuardReplaysAndConflicts() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        String digest = org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard.digest("{\"a\":1}");
        assertThat(WfTestEnvironment.flowIdempotencyGuard.begin(WfTestEnvironment.USER_EMPLOYEE, "k1",
            "/path", digest)).isNull();
        WfTestEnvironment.flowIdempotencyGuard.complete(WfTestEnvironment.USER_EMPLOYEE, "k1", "42");
        assertThat(WfTestEnvironment.flowIdempotencyGuard.begin(WfTestEnvironment.USER_EMPLOYEE, "k1",
            "/path", digest)).isEqualTo("42");
        assertThatThrownBy(() -> WfTestEnvironment.flowIdempotencyGuard.begin(WfTestEnvironment.USER_EMPLOYEE,
            "k1", "/path", org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard.digest("{\"a\":2}")))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
    }

    @Test
    void startFailureRollsBackBusinessInstanceRefAndOutbox() {
        WfTestEnvironment.jdbc().execute("UPDATE sys_dept SET leader = NULL WHERE dept_id = 10");
        WfTestEnvironment.jdbc().update("UPDATE oa_employee SET direct_leader_id = NULL WHERE id = 1000");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        BusinessRequestVo draft = createLeaveDraft();
        assertThatThrownBy(() -> WfTestEnvironment.inTransaction(() ->
            WfTestEnvironment.leaveService.submit(draft.getId(), draft.getLockVersion())))
            .isInstanceOf(ServiceException.class);
        assertThat(WfTestEnvironment.countInstances()).isZero();
        Long refs = WfTestEnvironment.jdbc().queryForObject("SELECT COUNT(*) FROM oa_flow_business_ref", Long.class);
        Long outbox = WfTestEnvironment.jdbc().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class);
        assertThat(refs).isZero();
        assertThat(outbox).isZero();
        assertThat(WfTestEnvironment.leaveService.selectRequest(draft.getId()).getStatus()).isEqualTo(1);
    }

    @Test
    void versionPinKeepsRunningInstancesOnTheirVersion() {
        InstanceStartVo start = submitLeave();
        Long instanceId = start.getInstanceId();
        Long versionIdBefore = WfTestEnvironment.jdbc().queryForObject(
            "SELECT definition_version_id FROM oa_flow_instance WHERE id = " + instanceId, Long.class);
        Long definitionId = WfTestEnvironment.jdbc().queryForObject(
            "SELECT definition_id FROM oa_flow_instance WHERE id = " + instanceId, Long.class);
        String procDefId = WfTestEnvironment.jdbc().queryForObject(
            "SELECT flowable_proc_def_id FROM oa_flow_definition_version WHERE id = " + versionIdBefore, String.class);
        WfTestEnvironment.jdbc().update("INSERT INTO oa_flow_definition_version"
            + "(id, definition_id, version_no, bpmn_resource, bpmn_digest, status, flowable_proc_def_id, create_time)"
            + " VALUES(900001, " + definitionId + ", 2, 'workflow/bpmn/leave.bpmn20.xml', 'x', 'PUBLISHED', '"
            + procDefId + "', CURRENT_TIMESTAMP)");
        WfTestEnvironment.jdbc().update("UPDATE oa_flow_definition SET current_version_no = 2 WHERE id = " + definitionId);

        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(instanceId);
        assertThat(detail.getDefinitionVersionNo()).isEqualTo(1);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        InstanceStartVo second = submitLeave();
        Long versionIdAfter = WfTestEnvironment.jdbc().queryForObject(
            "SELECT definition_version_id FROM oa_flow_instance WHERE id = " + second.getInstanceId(), Long.class);
        assertThat(versionIdAfter).isEqualTo(900001L);
        assertThat(WfTestEnvironment.instanceService.selectDetail(instanceId).getDefinitionVersionNo()).isEqualTo(1);
    }

    @Test
    void regularizeApprovalDrivesHrStateMachine() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        LifecycleRequestBo bo = new LifecycleRequestBo();
        bo.setEmployeeId(1000L);
        bo.setRequestType("REGULARIZE");
        bo.setEffectiveDate(LocalDate.now());
        bo.setFormData("{\"probationReview\":\"表现良好\"}");
        BusinessRequestVo draft = WfTestEnvironment.lifecycleService.createDraft(bo);
        InstanceStartVo start = WfTestEnvironment.lifecycleService.submit(draft.getId(), draft.getLockVersion());

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        agree(start.getCurrentTasks().get(0).getTaskId());
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of("hr:employee:edit"));
        agree(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords().get(0).getTaskId());

        String status = WfTestEnvironment.jdbc().queryForObject(
            "SELECT status FROM oa_employee WHERE id = 1000", String.class);
        assertThat(status).isEqualTo("ACTIVE");
        Long history = WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM oa_employee_history WHERE employee_id = 1000 AND event_type = 'PROBATION'", Long.class);
        assertThat(history).isEqualTo(1L);
    }

    @Test
    void offboardApprovalFreezesAccount() {
        WfTestEnvironment.jdbc().update("UPDATE oa_employee SET status = 'ACTIVE', user_id = "
            + WfTestEnvironment.USER_EMPLOYEE + " WHERE id = 1000");
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        LifecycleRequestBo bo = new LifecycleRequestBo();
        bo.setEmployeeId(1000L);
        bo.setRequestType("OFFBOARD");
        bo.setEffectiveDate(LocalDate.now());
        bo.setFormData("{\"leaveReason\":\"personal\"}");
        BusinessRequestVo draft = WfTestEnvironment.lifecycleService.createDraft(bo);
        InstanceStartVo start = WfTestEnvironment.lifecycleService.submit(draft.getId(), draft.getLockVersion());

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        agree(start.getCurrentTasks().get(0).getTaskId());
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of("hr:employee:edit"));
        agree(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords().get(0).getTaskId());

        assertThat(WfTestEnvironment.jdbc().queryForObject(
            "SELECT status FROM oa_employee WHERE id = 1000", String.class)).isEqualTo("LEFT");
        assertThat(WfTestEnvironment.jdbc().queryForObject(
            "SELECT status FROM sys_user WHERE user_id = " + WfTestEnvironment.USER_EMPLOYEE, String.class)).isEqualTo("1");
    }

    @Test
    void instanceDetailHiddenFromStrangers() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
    }

    // ------------------------------------------------------------ helpers

    private InstanceStartVo submitLeave() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        BusinessRequestVo draft = createLeaveDraft();
        return WfTestEnvironment.leaveService.submit(draft.getId(), draft.getLockVersion());
    }

    private BusinessRequestVo createLeaveDraft() {
        LeaveRequestBo bo = new LeaveRequestBo();
        bo.setLeaveType("annual");
        bo.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        bo.setEndTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0));
        bo.setReason("回家探亲");
        return WfTestEnvironment.leaveService.createDraft(bo);
    }

    private InstanceStartVo submitReimburse(String amount) {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        ReimburseRequestBo bo = new ReimburseRequestBo();
        bo.setReimburseType("expense");
        bo.setPayMethod("BANK_TRANSFER");
        ReimburseRequestBo.ReimburseDetailBo detail = new ReimburseRequestBo.ReimburseDetailBo();
        detail.setExpenseType("办公用品");
        detail.setOccurDate(LocalDate.now());
        detail.setAmount(amount);
        detail.setDescription("测试");
        bo.setDetails(List.of(detail));
        BusinessRequestVo draft = WfTestEnvironment.reimburseService.createDraft(bo);
        InstanceStartVo start = WfTestEnvironment.reimburseService.submit(draft.getId(), draft.getLockVersion());
        while (true) {
            List<TaskVo> current = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
            if (current.isEmpty()) {
                break;
            }
            TaskVo task = current.get(0);
            WfTestEnvironment.loginAs(assigneeOf(task), WfTestEnvironment.DEPT_A, Set.of());
            agree(task.getTaskId());
        }
        assertThat(WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getStatus()).isEqualTo(2);
        return start;
    }

    private List<String> chainOf(InstanceStartVo start) {
        return WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getActions().stream()
            .map(a -> a.getTaskName()).toList();
    }

    private Long assigneeOf(TaskVo task) {
        return switch (task.getTaskName()) {
            case "主管审批", "主管评价" -> WfTestEnvironment.USER_LEADER;
            case "财务审核" -> WfTestEnvironment.USER_FINANCE;
            case "出纳付款" -> WfTestEnvironment.USER_CASHIER;
            case "总监审批" -> WfTestEnvironment.USER_DIRECTOR;
            default -> WfTestEnvironment.USER_HR;
        };
    }

    private void agree(String taskId) {
        TaskCompleteBo bo = new TaskCompleteBo();
        bo.setAction("agree");
        bo.setComment("同意");
        WfTestEnvironment.taskService.complete(taskId, bo);
    }

    private org.dromara.agentoa.workflow.domain.bo.PageQuery newPage() {
        return new org.dromara.agentoa.workflow.domain.bo.PageQuery();
    }

    private void insertEmployee(Long id, Long userId, String status, Long leaderId) {
        WfTestEnvironment.jdbc().update("INSERT INTO oa_employee(id, user_id, employee_no, name, dept_id, direct_leader_id, status)"
                + " VALUES(?,?,?,?,?,?,?)",
            id, userId, "E" + id, "员工" + id, WfTestEnvironment.DEPT_A, leaderId, status);
    }
}
