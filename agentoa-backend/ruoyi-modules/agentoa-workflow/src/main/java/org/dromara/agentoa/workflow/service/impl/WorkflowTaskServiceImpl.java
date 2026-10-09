package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowCc;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.OaFlowTaskAction;
import org.dromara.agentoa.workflow.domain.bo.InstanceCcBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.bo.TaskAddsignBo;
import org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo;
import org.dromara.agentoa.workflow.domain.bo.TaskReturnBo;
import org.dromara.agentoa.workflow.domain.bo.TaskTransferBo;
import org.dromara.agentoa.workflow.domain.enums.FlowAction;
import org.dromara.agentoa.workflow.domain.enums.FlowInstanceStatus;
import org.dromara.agentoa.workflow.domain.policy.WorkflowAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.CcVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.mapper.OaFlowCcMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowTaskActionMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IWorkflowTaskService;
import org.dromara.agentoa.workflow.service.support.DelegationResolver;
import org.dromara.agentoa.workflow.service.support.OutboxWriter;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.HistoryService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowTaskServiceImpl implements IWorkflowTaskService {

    private final OaFlowInstanceMapper instanceMapper;
    private final OaFlowTaskActionMapper taskActionMapper;
    private final OaFlowCcMapper ccMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final OutboxWriter outboxWriter;
    private final DelegationResolver delegationResolver;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public PageVo<TaskVo> selectTodo(PageQuery page) {
        Long userId = LoginHelper.getUserId();
        List<String> assigneeIds = new ArrayList<>();
        assigneeIds.add(String.valueOf(userId));
        for (Long owner : delegationResolver.delegatedOwnerIds(userId, null)) {
            assigneeIds.add(String.valueOf(owner));
        }
        // 本人办理 + 本人候选 + 委托代办，多源合并后手动分页（委托人数有限）
        Map<String, Task> merged = new java.util.LinkedHashMap<>();
        for (String assigneeId : assigneeIds) {
            List<Task> owned = taskService.createTaskQuery().taskAssignee(assigneeId)
                .orderByTaskCreateTime().desc().list();
            owned.forEach(t -> merged.putIfAbsent(t.getId(), t));
        }
        List<Task> candidates = taskService.createNativeTaskQuery()
            .sql("select RES.* from ACT_RU_TASK RES inner join ACT_RU_IDENTITYLINK I on I.TASK_ID_ = RES.ID_ "
                + "where I.TYPE_ = 'candidate' and I.USER_ID_ = #{candidateUser}")
            .parameter("candidateUser", String.valueOf(userId))
            .list();
        candidates.forEach(t -> merged.putIfAbsent(t.getId(), t));
        long total = merged.size();
        List<Task> tasks = merged.values().stream()
            .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
            .skip((long) (page.safePageNum() - 1) * page.safePageSize())
            .limit(page.safePageSize())
            .toList();
        Map<String, OaFlowInstance> byProcId = instancesOf(tasks.stream().map(Task::getProcessInstanceId).toList());
        List<TaskVo> records = new ArrayList<>();
        for (Task task : tasks) {
            OaFlowInstance instance = byProcId.get(task.getProcessInstanceId());
            if (instance != null) {
                records.add(toTaskVo(instance, task, null));
            }
        }
        return PageVo.of(records, total, page.safePageNum(), page.safePageSize());
    }

    @Override
    public PageVo<TaskVo> selectDone(PageQuery page) {
        Long userId = LoginHelper.getUserId();
        long total = historyService.createHistoricTaskInstanceQuery()
            .taskAssignee(String.valueOf(userId)).finished().count();
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
            .taskAssignee(String.valueOf(userId)).finished()
            .orderByHistoricTaskInstanceEndTime().desc()
            .listPage((page.safePageNum() - 1) * page.safePageSize(), page.safePageSize());
        Map<String, OaFlowInstance> byProcId = instancesOf(tasks.stream()
            .map(HistoricTaskInstance::getProcessInstanceId).toList());
        List<TaskVo> records = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            OaFlowInstance instance = byProcId.get(task.getProcessInstanceId());
            if (instance != null) {
                TaskVo vo = toTaskVo(instance, null, task);
                vo.setEndTime(task.getEndTime());
                records.add(vo);
            }
        }
        return PageVo.of(records, total, page.safePageNum(), page.safePageSize());
    }

    @Override
    public TaskVo selectTask(String taskId) {
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        Long userId = LoginHelper.getUserId();
        boolean participant = orchestrator.assigneesOf(task).contains(userId)
            || instance.getInitiatorUserId().equals(userId)
            || taskActionMapper.selectCount(new LambdaQueryWrapper<OaFlowTaskAction>()
                .eq(OaFlowTaskAction::getInstanceId, instance.getId())
                .eq(OaFlowTaskAction::getOperatorUserId, userId)) > 0;
        if (!WorkflowAccessPolicy.canViewInstance(LoginHelper.isSuperAdmin(), false, userId,
            instance.getInitiatorUserId(), participant)) {
            throw new ServiceException("WF_NOT_FOUND 任务不存在或对当前用户不可见", 404);
        }
        return toTaskVo(instance, task, null);
    }

    @Override
    public TaskVo selectTaskResult(String flowableTaskId) {
        Task task = taskService.createTaskQuery().taskId(flowableTaskId).singleResult();
        if (task != null) {
            OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
            return toTaskVo(instance, task, null);
        }
        HistoricTaskInstance historic = historyService.createHistoricTaskInstanceQuery()
            .taskId(flowableTaskId).singleResult();
        if (historic == null) {
            throw new ServiceException("WF_NOT_FOUND 任务不存在", 404);
        }
        OaFlowInstance instance = requireInstance(historic.getProcessInstanceId());
        TaskVo vo = toTaskVo(instance, null, historic);
        vo.setEndTime(historic.getEndTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVo complete(String taskId, TaskCompleteBo bo) {
        Long userId = LoginHelper.getUserId();
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        checkRunnable(task, instance, userId, bo.getLockVersion());

        Long oldAssignee = task.getAssignee() != null ? Long.valueOf(task.getAssignee()) : null;
        FlowAction action = FlowAction.from(bo.getAction());
        String operatorName = identityMapper.selectNickName(userId);

        if (action == FlowAction.AGREE) {
            if (task.getAssignee() == null) {
                taskService.setAssignee(task.getId(), String.valueOf(userId));
            }
            // 后加签队列（P1）：同意后按顺序转给加签人续办，队列清空才推进节点
            String afterQueue = (String) taskService.getVariableLocal(task.getId(), "oaAfterSigners");
            if (afterQueue != null && !afterQueue.isBlank()) {
                List<String> queue = new ArrayList<>(List.of(afterQueue.split(",")));
                String next = queue.remove(0);
                taskService.setVariableLocal(task.getId(), "oaAfterSigners", String.join(",", queue));
                taskService.setAssignee(task.getId(), next);
                writeAction(instance, task, FlowAction.ADDSIGN, userId, operatorName,
                    oldAssignee, Long.valueOf(next), "后加签转办：" + StringUtils.defaultString(bo.getComment()));
                refreshSnapshot(instance);
                notifyCurrent(instance);
                return toTaskVo(instanceMapper.selectById(instance.getId()), task, null);
            }
            try {
                if (bo.getComment() != null && !bo.getComment().isBlank()) {
                    taskService.addComment(task.getId(), task.getProcessInstanceId(), bo.getComment());
                }
                taskService.complete(task.getId());
            } catch (Throwable e) {
                throw WorkflowOrchestrator.translate(e);
            }
            writeAction(instance, task, action, userId, operatorName, oldAssignee, null, bo.getComment());
            boolean finished = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).count() == 0;
            if (finished) {
                orchestrator.approveFinished(instance.getId(), userId);
            } else {
                refreshSnapshot(instance);
                notifyCurrent(instance);
            }
        } else {
            writeAction(instance, task, action, userId, operatorName, oldAssignee, null, bo.getComment());
            orchestrator.rejectInstance(instance.getId(), userId);
            outboxWriter.writeSystem("FREJECT-" + instance.getId(), instance.getInitiatorUserId(),
                "您的流程已被拒绝", instance.getTitle());
        }
        instance = instanceMapper.selectById(instance.getId());
        return toTaskVo(instance, task, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVo transfer(String taskId, TaskTransferBo bo) {
        Long userId = LoginHelper.getUserId();
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        checkRunnable(task, instance, userId, null);

        String targetName = identityMapper.selectUserName(bo.getTargetUserId());
        if (targetName == null) {
            throw new ServiceException("目标办理人不存在", 404);
        }
        Long oldAssignee = task.getAssignee() != null ? Long.valueOf(task.getAssignee()) : userId;
        try {
            taskService.setAssignee(task.getId(), String.valueOf(bo.getTargetUserId()));
        } catch (Throwable e) {
            throw WorkflowOrchestrator.translate(e);
        }
        writeAction(instance, task, FlowAction.TRANSFER, userId, identityMapper.selectNickName(userId),
            oldAssignee, bo.getTargetUserId(), bo.getReason());
        refreshSnapshot(instance);
        try {
            outboxWriter.writeTodo("FTODO-" + instance.getId() + "-" + task.getId() + "-" + bo.getTargetUserId(),
                bo.getTargetUserId(), "您有一条新的待办", instance.getTitle(), "flow_instance", instance.getId(),
                "/flow/todo/" + instance.getId());
        } catch (DuplicateKeyException ignored) {
            // 目标办理人已有同任务待办事件，不重复投递
        }
        return toTaskVo(instance, task, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVo returnTask(String taskId, TaskReturnBo bo) {
        Long userId = LoginHelper.getUserId();
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        checkRunnable(task, instance, userId, bo.getLockVersion());
        if (isMultiInstanceTask(task)) {
            throw new ServiceException("WF_STATE_CONFLICT 多实例节点不支持退回，仅单实例节点可用", 400);
        }

        String targetKey = resolveReturnTarget(task, bo);
        if (targetKey.equals(task.getTaskDefinitionKey())) {
            throw new ServiceException("WF_STATE_CONFLICT 目标节点不能是当前节点", 409);
        }
        try {
            runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), targetKey)
                .changeState();
        } catch (Throwable e) {
            throw WorkflowOrchestrator.translate(e);
        }
        writeAction(instance, task, FlowAction.RETURN, userId, identityMapper.selectNickName(userId),
            task.getAssignee() != null ? Long.valueOf(task.getAssignee()) : null, null,
            (Boolean.TRUE.equals(bo.getToInitiator()) ? "退回到发起节点" : "退回到节点 " + targetKey)
                + (StringUtils.isBlank(bo.getComment()) ? "" : "：" + bo.getComment()));
        refreshSnapshot(instance);
        notifyCurrent(instance);
        return toTaskVo(instanceMapper.selectById(instance.getId()), task, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVo addsign(String taskId, TaskAddsignBo bo) {
        Long userId = LoginHelper.getUserId();
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        checkRunnable(task, instance, userId, null);
        if (isMultiInstanceTask(task)) {
            throw new ServiceException("WF_STATE_CONFLICT 多实例节点不支持加签，仅单实例节点可用", 409);
        }
        boolean before = bo.getPosition() == null || "before".equals(bo.getPosition());
        for (Long assigneeId : bo.getAssigneeIds()) {
            if (identityMapper.selectUserName(assigneeId) == null) {
                throw new ServiceException("加签人不存在: " + assigneeId, 404);
            }
        }
        Long oldAssignee = task.getAssignee() != null ? Long.valueOf(task.getAssignee()) : userId;
        if (before) {
            for (Long assigneeId : bo.getAssigneeIds()) {
                taskService.addCandidateUser(task.getId(), String.valueOf(assigneeId));
            }
        } else {
            Object existing = taskService.getVariableLocal(task.getId(), "oaAfterSigners");
            StringBuilder queue = new StringBuilder(existing == null ? "" : existing + ",");
            for (int i = 0; i < bo.getAssigneeIds().size(); i++) {
                if (i > 0) {
                    queue.append(',');
                }
                queue.append(bo.getAssigneeIds().get(i));
            }
            taskService.setVariableLocal(task.getId(), "oaAfterSigners", queue.toString());
        }
        for (Long assigneeId : bo.getAssigneeIds()) {
            writeAction(instance, task, FlowAction.ADDSIGN, userId, identityMapper.selectNickName(userId),
                oldAssignee, assigneeId,
                (before ? "[前加签]" : "[后加签]") + (StringUtils.isBlank(bo.getReason()) ? "" : " " + bo.getReason()));
        }
        refreshSnapshot(instance);
        notifyCurrent(instance);
        return toTaskVo(instanceMapper.selectById(instance.getId()), task, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void urge(String taskId, String comment) {
        Long userId = LoginHelper.getUserId();
        Task task = requireTask(taskId);
        OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
        if (!instance.getInitiatorUserId().equals(userId) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("WF_FORBIDDEN 仅发起人可催办", 403);
        }
        if (instance.getStatus() != FlowInstanceStatus.RUNNING.code()) {
            throw new ServiceException("WF_STATE_CONFLICT 流程已结束，不能催办", 409);
        }
        writeAction(instance, task, FlowAction.URGE, userId, identityMapper.selectNickName(userId),
            null, null, StringUtils.isBlank(comment) ? "发起人催办" : comment);
        String day = LocalDate.now().toString();
        for (Long assigneeId : orchestrator.assigneesOf(task)) {
            try {
                outboxWriter.writeSystem("FURGE-" + instance.getId() + "-" + task.getId() + "-" + assigneeId + "-" + day,
                    assigneeId, "您有流程待办被催办", instance.getTitle());
            } catch (DuplicateKeyException ignored) {
                // 同一任务同一天只提醒一次
            }
        }
    }

    @Override
    public PageVo<CcVo> selectCc(PageQuery page) {
        Long userId = LoginHelper.getUserId();
        Page<OaFlowCc> mpPage = new Page<>(page.safePageNum(), page.safePageSize());
        Page<OaFlowCc> result = ccMapper.selectPage(mpPage, new LambdaQueryWrapper<OaFlowCc>()
            .eq(OaFlowCc::getCcUserId, userId)
            .orderByDesc(OaFlowCc::getCreateTime));
        List<CcVo> records = result.getRecords().stream().map(this::toCcVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cc(InstanceCcBo bo, Long instanceId) {
        Long userId = LoginHelper.getUserId();
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        boolean participant = instance.getInitiatorUserId().equals(userId)
            || taskActionMapper.selectCount(new LambdaQueryWrapper<OaFlowTaskAction>()
            .eq(OaFlowTaskAction::getInstanceId, instanceId)
            .eq(OaFlowTaskAction::getOperatorUserId, userId)) > 0;
        if (!WorkflowAccessPolicy.canViewInstance(LoginHelper.isSuperAdmin(), false, userId,
            instance.getInitiatorUserId(), participant)) {
            throw new ServiceException("WF_FORBIDDEN 无抄送权限", 403);
        }
        for (Long ccUserId : bo.getUserIds()) {
            if (ccUserId.equals(userId)) {
                continue;
            }
            OaFlowCc record = new OaFlowCc();
            record.setInstanceId(instanceId);
            record.setSenderUserId(userId);
            record.setCcUserId(ccUserId);
            record.setComment(bo.getComment());
            record.setEventId("FCC-" + instanceId + "-" + ccUserId);
            record.setCreateTime(new Date());
            try {
                ccMapper.insert(record);
            } catch (DuplicateKeyException e) {
                continue;
            }
            writeAction(instance, null, FlowAction.CC, userId, identityMapper.selectNickName(userId),
                null, ccUserId, bo.getComment());
            outboxWriter.writeSystem("FCC-" + instanceId + "-" + ccUserId, ccUserId,
                "您有一条流程抄送", instance.getTitle());
        }
    }

    private String resolveReturnTarget(Task task, TaskReturnBo bo) {
        if (StringUtils.isNotBlank(bo.getTargetTaskKey())) {
            return bo.getTargetTaskKey();
        }
        if (Boolean.TRUE.equals(bo.getToInitiator())) {
            List<HistoricActivityInstance> history = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc().list();
            if (history.isEmpty()) {
                throw new ServiceException("WF_STATE_CONFLICT 无可退回的发起节点", 409);
            }
            return history.get(0).getActivityId();
        }
        throw new ServiceException("退回目标不能为空", 400);
    }

    private CcVo toCcVo(OaFlowCc record) {
        CcVo vo = new CcVo();
        vo.setId(record.getId());
        vo.setInstanceId(record.getInstanceId());
        vo.setSenderUserId(record.getSenderUserId());
        vo.setSenderName(identityMapper.selectNickName(record.getSenderUserId()));
        vo.setComment(record.getComment());
        vo.setCreateTime(record.getCreateTime());
        vo.setReadTime(record.getReadTime());
        OaFlowInstance instance = instanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            vo.setTitle(instance.getTitle());
            vo.setBusinessType(instance.getBusinessType());
            vo.setBusinessId(instance.getBusinessId());
            vo.setInstanceStatus(instance.getStatus());
        }
        return vo;
    }

    private void checkRunnable(Task task, OaFlowInstance instance, Long userId, Integer lockVersion) {
        if (instance.getStatus() != FlowInstanceStatus.RUNNING.code()) {
            throw new ServiceException("WF_STATE_CONFLICT 流程状态已改变", 409);
        }
        if (task.isSuspended()) {
            throw new ServiceException("WF_STATE_CONFLICT 任务已挂起", 409);
        }
        if (!WorkflowAccessPolicy.canActOnTask(isAssigneeOrCandidate(task, userId))) {
            throw new ServiceException("WF_FORBIDDEN 无任务办理权限", 403);
        }
        if (lockVersion != null && !lockVersion.equals(instance.getLockVersion())) {
            throw new ServiceException("VERSION_CONFLICT 业务版本已改变", 409);
        }
    }

    /** 多实例子任务标记（WF-08）：加签/退回限制在单实例节点 */
    private boolean isMultiInstanceTask(Task task) {
        return taskService.getVariableLocal(task.getId(),
            org.dromara.agentoa.workflow.assigner.AssigneeTaskListener.MI_MARKER) != null;
    }

    private boolean isAssigneeOrCandidate(Task task, Long userId) {
        if (task.getAssignee() != null && task.getAssignee().equals(String.valueOf(userId))) {
            return true;
        }
        if (task.getAssignee() != null) {
            OaFlowInstance instance = requireInstance(task.getProcessInstanceId());
            if (delegationResolver.canActAsDelegate(userId, Long.valueOf(task.getAssignee()), instance.getBusinessType())) {
                return true;
            }
        }
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if ("candidate".equals(link.getType()) && link.getUserId() != null
                && link.getUserId().equals(String.valueOf(userId))) {
                return true;
            }
        }
        return false;
    }

    private void writeAction(OaFlowInstance instance, Task task, FlowAction action, Long operatorUserId,
                             String operatorName, Long oldAssigneeId, Long newAssigneeId, String comment) {
        OaFlowTaskAction record = new OaFlowTaskAction();
        record.setInstanceId(instance.getId());
        record.setFlowableTaskId(task == null ? null : task.getId());
        record.setTaskName(task == null ? action.key() : task.getName());
        record.setTaskDefKey(task == null ? null : task.getTaskDefinitionKey());
        record.setAction(action.key());
        record.setOperatorUserId(operatorUserId);
        record.setOperatorName(operatorName);
        record.setOldAssigneeId(oldAssigneeId);
        record.setOldAssigneeName(oldAssigneeId == null ? null : identityMapper.selectNickName(oldAssigneeId));
        record.setNewAssigneeId(newAssigneeId);
        record.setNewAssigneeName(newAssigneeId == null ? null : identityMapper.selectNickName(newAssigneeId));
        record.setComment(comment);
        record.setActionTime(new Date());
        taskActionMapper.insert(record);
    }

    private void refreshSnapshot(OaFlowInstance instance) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(instance.getFlowableProcInstId()).list();
        orchestrator.applyTaskSnapshot(instance, tasks);
        instance.setLockVersion(instance.getLockVersion() + 1);
        instanceMapper.updateById(instance);
    }

    private void notifyCurrent(OaFlowInstance instance) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(instance.getFlowableProcInstId()).list();
        for (Task task : tasks) {
            for (Long userId : orchestrator.assigneesOf(task)) {
                try {
                    outboxWriter.writeTodo("FTODO-" + instance.getId() + "-" + task.getId() + "-" + userId, userId,
                        "您有一条新的待办", instance.getTitle(), "flow_instance", instance.getId(),
                        "/flow/todo/" + instance.getId());
                } catch (DuplicateKeyException ignored) {
                    // 同一任务同一办理人只投递一次待办事件
                }
            }
        }
    }

    private Task requireTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ServiceException("WF_NOT_FOUND 任务不存在或不可见", 404);
        }
        return task;
    }

    private OaFlowInstance requireInstance(String processInstanceId) {
        OaFlowInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<OaFlowInstance>()
            .eq(OaFlowInstance::getFlowableProcInstId, processInstanceId));
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        return instance;
    }

    private Map<String, OaFlowInstance> instancesOf(List<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        return instanceMapper.selectList(new LambdaQueryWrapper<OaFlowInstance>()
                .in(OaFlowInstance::getFlowableProcInstId, processInstanceIds))
            .stream().collect(Collectors.toMap(OaFlowInstance::getFlowableProcInstId, Function.identity()));
    }

    private TaskVo toTaskVo(OaFlowInstance instance, Task task, HistoricTaskInstance historic) {
        TaskVo vo = new TaskVo();
        String taskId = task != null ? task.getId() : (historic != null ? historic.getId() : null);
        vo.setTaskId(taskId);
        vo.setInstanceId(instance.getId());
        vo.setProcessInstanceId(instance.getFlowableProcInstId());
        vo.setTaskName(task != null ? task.getName() : historic.getName());
        vo.setTaskDefKey(task != null ? task.getTaskDefinitionKey() : historic.getTaskDefinitionKey());
        String assignee = task != null ? task.getAssignee() : historic.getAssignee();
        if (assignee != null) {
            vo.setAssigneeId(Long.valueOf(assignee));
            vo.setAssigneeName(identityMapper.selectNickName(Long.valueOf(assignee)));
        }
        vo.setBusinessType(instance.getBusinessType());
        vo.setBusinessId(instance.getBusinessId());
        vo.setTitle(instance.getTitle());
        vo.setInstanceStatus(instance.getStatus());
        vo.setInitiatorName(instance.getInitiatorName());
        vo.setCreateTime(task != null ? task.getCreateTime() : historic.getCreateTime());
        return vo;
    }
}
