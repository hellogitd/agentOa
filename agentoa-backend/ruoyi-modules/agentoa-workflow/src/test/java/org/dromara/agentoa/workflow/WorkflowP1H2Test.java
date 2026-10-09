package org.dromara.agentoa.workflow;

import org.dromara.agentoa.workflow.domain.bo.DelegateBo;
import org.dromara.agentoa.workflow.domain.bo.InstanceCcBo;
import org.dromara.agentoa.workflow.domain.bo.LeaveRequestBo;
import org.dromara.agentoa.workflow.domain.bo.TaskAddsignBo;
import org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo;
import org.dromara.agentoa.workflow.domain.bo.TaskReturnBo;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.CcVo;
import org.dromara.agentoa.workflow.domain.vo.DelegateVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceDetailVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.support.WfTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 + Flowable 集成测试（P1）：挂起/恢复/终止、退回、加签、催办、抄送、委托代理与超时扫描。
 */
class WorkflowP1H2Test {

    private static final Set<String> MONITOR_PERMS = Set.of(
        "wf:instance:list", "wf:instance:suspend", "wf:instance:resume", "wf:instance:terminate");

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
            "INSERT INTO sys_dept(dept_id, parent_id, ancestors, dept_name, leader) VALUES(10, 0, '0', 'test-dept', NULL)");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, "leader");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, "employee");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, "other");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, "hr");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_FINANCE, WfTestEnvironment.DEPT_A, "finance");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_CASHIER, WfTestEnvironment.DEPT_A, "cashier");
        WfTestEnvironment.createSysUser(WfTestEnvironment.USER_DIRECTOR, WfTestEnvironment.DEPT_A, "director");
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_HR, 20L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_LEADER, 21L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_EMPLOYEE, 22L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_FINANCE, 23L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_CASHIER, 24L);
        WfTestEnvironment.grantRole(WfTestEnvironment.USER_DIRECTOR, 25L);
        insertEmployee(1000L, WfTestEnvironment.USER_EMPLOYEE, "PROBATION", WfTestEnvironment.USER_LEADER);
        insertEmployee(1001L, WfTestEnvironment.USER_OTHER, "ACTIVE", WfTestEnvironment.USER_LEADER);
    }

    // ---------------------------------------------------------------- 挂起 / 恢复 / 终止

    @Test
    void suspendBlocksCompleteAndResumeRestores() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, MONITOR_PERMS);
        assertThat(WfTestEnvironment.instanceService.suspend(start.getInstanceId()).getStatus()).isEqualTo(5);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> agree(taskId))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, MONITOR_PERMS);
        assertThat(WfTestEnvironment.instanceService.resume(start.getInstanceId()).getStatus()).isEqualTo(1);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        agree(taskId);
        assertThat(WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks()).hasSize(1);
    }

    @Test
    void suspendIsIdempotentConflictOnDoubleSuspend() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, MONITOR_PERMS);
        WfTestEnvironment.instanceService.suspend(start.getInstanceId());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.suspend(start.getInstanceId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
    }

    @Test
    void terminateEndsInstanceAndBusinessSide() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, MONITOR_PERMS);
        assertThat(WfTestEnvironment.instanceService.terminate(start.getInstanceId(), "测试终止").getStatus()).isEqualTo(6);

        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId());
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        BusinessRequestVo request = WfTestEnvironment.leaveService.selectRequest(detail.getBusinessId());
        assertThat(request.getStatus()).isEqualTo(7);
        assertThat(detail.getActions()).extracting(a -> a.getAction()).contains("terminate");

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> agree(start.getCurrentTasks().get(0).getTaskId()))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void terminateRequiresMonitorPermission() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.instanceService.terminate(start.getInstanceId(), "x"))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(403));
    }

    // ---------------------------------------------------------------- 退回

    @Test
    void returnMovesFlowBackToPreviousNode() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        agree(start.getCurrentTasks().get(0).getTaskId());

        List<TaskVo> hrTasks = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
        assertThat(hrTasks).hasSize(1);
        String hrTaskId = hrTasks.get(0).getTaskId();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, Set.of());
        TaskReturnBo bo = new TaskReturnBo();
        bo.setTargetTaskKey("leaderApprove");
        bo.setComment("补充材料");
        WfTestEnvironment.taskService.returnTask(hrTaskId, bo);

        List<TaskVo> current = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
        assertThat(current).hasSize(1);
        assertThat(current.get(0).getTaskDefKey()).isEqualTo("leaderApprove");
        InstanceDetailVo detail = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId());
        assertThat(detail.getActions()).extracting(a -> a.getAction()).contains("return");
    }

    @Test
    void returnToSameNodeConflicts() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        TaskReturnBo bo = new TaskReturnBo();
        bo.setTargetTaskKey("leaderApprove");
        assertThatThrownBy(() -> WfTestEnvironment.taskService.returnTask(start.getCurrentTasks().get(0).getTaskId(), bo))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
    }

    // ---------------------------------------------------------------- 加签

    @Test
    void addsignBeforeAddsCoHandler() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        TaskAddsignBo bo = new TaskAddsignBo();
        bo.setAssigneeIds(List.of(WfTestEnvironment.USER_OTHER));
        bo.setPosition("before");
        bo.setReason("会办");
        WfTestEnvironment.taskService.addsign(taskId, bo);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords())
            .extracting(TaskVo::getTaskId).contains(taskId);
        agree(taskId);
        assertThat(WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks())
            .hasSize(1);
    }

    @Test
    void addsignAfterRoutesSequentiallyBeforeAdvance() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        TaskAddsignBo bo = new TaskAddsignBo();
        bo.setAssigneeIds(List.of(WfTestEnvironment.USER_OTHER));
        bo.setPosition("after");
        bo.setReason("后加签复核");
        WfTestEnvironment.taskService.addsign(taskId, bo);
        agree(taskId);

        // 后加签人续办：仍停在当前节点
        List<TaskVo> current = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
        assertThat(current).hasSize(1);
        assertThat(current.get(0).getTaskDefKey()).isEqualTo("leaderApprove");
        assertThat(current.get(0).getAssigneeId()).isEqualTo(WfTestEnvironment.USER_OTHER);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        agree(current.get(0).getTaskId());
        List<TaskVo> next = WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks();
        assertThat(next).hasSize(1);
        assertThat(next.get(0).getTaskDefKey()).isNotEqualTo("leaderApprove");
    }

    // ---------------------------------------------------------------- 催办

    @Test
    void urgeOnlyInitiatorAndDedupPerDay() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        assertThatThrownBy(() -> WfTestEnvironment.taskService.urge(taskId, "催"))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(403));

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        WfTestEnvironment.taskService.urge(taskId, "请尽快处理");
        WfTestEnvironment.taskService.urge(taskId, "再催一次");
        Long outbox = WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM sys_outbox WHERE event_id LIKE 'FURGE-%'", Long.class);
        assertThat(outbox).isEqualTo(1L);
        assertThat(WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getActions())
            .extracting(a -> a.getAction()).contains("urge");
    }

    // ---------------------------------------------------------------- 抄送

    @Test
    void ccRecordsOnceAndNotifies() {
        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        InstanceCcBo bo = new InstanceCcBo();
        bo.setUserIds(List.of(WfTestEnvironment.USER_OTHER));
        bo.setComment("请知悉");
        WfTestEnvironment.taskService.cc(bo, start.getInstanceId());
        WfTestEnvironment.taskService.cc(bo, start.getInstanceId());

        Long rows = WfTestEnvironment.jdbc().queryForObject(
            "SELECT COUNT(*) FROM oa_flow_cc WHERE instance_id = ?", Long.class, start.getInstanceId());
        assertThat(rows).isEqualTo(1L);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        List<CcVo> ccList = WfTestEnvironment.taskService.selectCc(newPage()).getRecords();
        assertThat(ccList).hasSize(1);
        assertThat(ccList.get(0).getInstanceId()).isEqualTo(start.getInstanceId());
        assertThat(ccList.get(0).getComment()).isEqualTo("请知悉");
    }

    // ---------------------------------------------------------------- 委托代理

    @Test
    void delegateCanViewAndActOnOwnerTasks() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        DelegateBo bo = new DelegateBo();
        bo.setDelegateId(WfTestEnvironment.USER_OTHER);
        bo.setStartDate(LocalDate.now().minusDays(1));
        bo.setEndDate(LocalDate.now().plusDays(7));
        DelegateVo delegate = WfTestEnvironment.delegateService.createDelegate(bo);
        assertThat(delegate.getOwnerId()).isEqualTo(WfTestEnvironment.USER_LEADER);

        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords())
            .extracting(TaskVo::getTaskId).contains(taskId);
        agree(taskId);
        assertThat(WfTestEnvironment.instanceService.selectDetail(start.getInstanceId()).getCurrentTasks()).hasSize(1);
    }

    @Test
    void delegateStopsWhenDisabledOrExpired() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        DelegateBo bo = new DelegateBo();
        bo.setDelegateId(WfTestEnvironment.USER_OTHER);
        bo.setStartDate(LocalDate.now().minusDays(10));
        bo.setEndDate(LocalDate.now().minusDays(1));
        WfTestEnvironment.delegateService.createDelegate(bo);

        InstanceStartVo start = submitLeave();
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_OTHER, WfTestEnvironment.DEPT_A, Set.of());
        assertThat(WfTestEnvironment.taskService.selectTodo(newPage()).getRecords()).isEmpty();
        assertThatThrownBy(() -> agree(start.getCurrentTasks().get(0).getTaskId()))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(403));
    }

    @Test
    void delegateCannotTargetSelf() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_LEADER, WfTestEnvironment.DEPT_A, Set.of());
        DelegateBo bo = new DelegateBo();
        bo.setDelegateId(WfTestEnvironment.USER_LEADER);
        bo.setStartDate(LocalDate.now());
        bo.setEndDate(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> WfTestEnvironment.delegateService.createDelegate(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("本人");
    }

    // ---------------------------------------------------------------- 超时扫描

    @Test
    void timeoutScanMarksInstanceAndRemindsOnce() {
        InstanceStartVo start = submitLeave();
        String taskId = start.getCurrentTasks().get(0).getTaskId();
        WfTestEnvironment.jdbc().update(
            "UPDATE oa_flow_instance SET start_time = ? WHERE id = ?",
            LocalDateTime.now().minusDays(2), start.getInstanceId());
        WfTestEnvironment.jdbc().update(
            "UPDATE ACT_RU_TASK SET CREATE_TIME_ = ? WHERE ID_ = ?",
            LocalDateTime.now().minusDays(2), taskId);

        WfTestEnvironment.loginAs(WfTestEnvironment.USER_HR, WfTestEnvironment.DEPT_A, MONITOR_PERMS);
        assertThat(WfTestEnvironment.instanceService.scanTimeouts(1)).isEqualTo(1);
        assertThat(WfTestEnvironment.instanceService.scanTimeouts(1)).isZero();

        Integer flag = WfTestEnvironment.jdbc().queryForObject(
            "SELECT is_timeout FROM oa_flow_instance WHERE id = ?", Integer.class, start.getInstanceId());
        assertThat(flag).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 辅助

    private InstanceStartVo submitLeave() {
        WfTestEnvironment.loginAs(WfTestEnvironment.USER_EMPLOYEE, WfTestEnvironment.DEPT_A, Set.of());
        LeaveRequestBo bo = new LeaveRequestBo();
        bo.setLeaveType("annual");
        bo.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        bo.setEndTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0));
        bo.setReason("home");
        BusinessRequestVo draft = WfTestEnvironment.leaveService.createDraft(bo);
        return WfTestEnvironment.leaveService.submit(draft.getId(), draft.getLockVersion());
    }

    private void agree(String taskId) {
        TaskCompleteBo bo = new TaskCompleteBo();
        bo.setAction("agree");
        bo.setComment("ok");
        WfTestEnvironment.taskService.complete(taskId, bo);
    }

    private org.dromara.agentoa.workflow.domain.bo.PageQuery newPage() {
        return new org.dromara.agentoa.workflow.domain.bo.PageQuery();
    }

    private void insertEmployee(Long id, Long userId, String status, Long leaderId) {
        WfTestEnvironment.jdbc().update("INSERT INTO oa_employee(id, user_id, employee_no, name, dept_id, direct_leader_id, status)"
                + " VALUES(?,?,?,?,?,?,?)",
            id, userId, "E" + id, "emp" + id, WfTestEnvironment.DEPT_A, leaderId, status);
    }
}
