package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowDefinition;
import org.dromara.agentoa.workflow.domain.OaFlowDefinitionVersion;
import org.dromara.agentoa.workflow.domain.OaFlowFormVersion;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.OaFlowTaskAction;
import org.dromara.agentoa.workflow.domain.bo.InstanceStartBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.enums.FlowInstanceStatus;
import org.dromara.agentoa.workflow.domain.policy.WorkflowAccessPolicy;
import org.dromara.agentoa.workflow.domain.vo.InstanceDetailVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceVo;
import org.dromara.agentoa.workflow.font.DiagramFontProvider;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.TaskActionVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowTaskActionMapper;
import org.dromara.agentoa.workflow.service.IWorkflowInstanceService;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowInstanceServiceImpl implements IWorkflowInstanceService {

    private final OaFlowInstanceMapper instanceMapper;
    private final OaFlowTaskActionMapper taskActionMapper;
    private final OaFlowDefinitionMapper definitionMapper;
    private final OaFlowDefinitionVersionMapper versionMapper;
    private final OaFlowFormVersionMapper formVersionMapper;
    private final WorkflowOrchestrator orchestrator;
    private final org.dromara.agentoa.workflow.service.support.OutboxWriter outboxWriter;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;

    @Override
    public InstanceStartVo start(InstanceStartBo bo) {
        BusinessType.from(bo.getBusinessType() == null ? "generic" : bo.getBusinessType());
        return orchestrator.start(bo.getBusinessType(), bo.getDefinitionId(), bo.getBusinessId(),
            bo.getLockVersion(), LoginHelper.getUserId(), bo.getTitle(), bo.getPriority(), bo.getAssigneeSelections());
    }

    @Override
    public InstanceStartVo selectStartResult(Long instanceId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        InstanceStartVo vo = new InstanceStartVo();
        vo.setInstanceId(instance.getId());
        vo.setProcessInstanceId(instance.getFlowableProcInstId());
        vo.setBusinessKey(instance.getBusinessKey());
        for (Task task : taskService.createTaskQuery().processInstanceId(instance.getFlowableProcInstId()).list()) {
            InstanceStartVo.CurrentTaskVo taskVo = new InstanceStartVo.CurrentTaskVo();
            taskVo.setTaskId(task.getId());
            taskVo.setTaskName(task.getName());
            if (task.getAssignee() != null) {
                taskVo.setAssigneeId(Long.valueOf(task.getAssignee()));
            }
            vo.getCurrentTasks().add(taskVo);
        }
        return vo;
    }

    @Override
    public PageVo<InstanceVo> selectPage(String scope, String businessType, PageQuery page) {
        Long userId = LoginHelper.getUserId();
        boolean monitor = hasMonitorScope();
        LambdaQueryWrapper<OaFlowInstance> query = new LambdaQueryWrapper<OaFlowInstance>();
        if (businessType != null && !businessType.isBlank()) {
            query.eq(OaFlowInstance::getBusinessType, BusinessType.from(businessType).key());
        }
        if (!"all".equals(scope)) {
            query.eq(OaFlowInstance::getInitiatorUserId, userId);
        } else if (!monitor && !LoginHelper.isSuperAdmin()) {
            query.eq(OaFlowInstance::getInitiatorUserId, userId);
        }
        query.orderByDesc(OaFlowInstance::getStartTime);
        IPage<OaFlowInstance> result = instanceMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<InstanceVo> records = result.getRecords().stream().map(this::toInstanceVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceDetailVo selectDetail(Long instanceId) {
        OaFlowInstance instance = requireVisibleInstance(instanceId);
        InstanceDetailVo vo = new InstanceDetailVo();
        copy(toInstanceVo(instance), vo);
        OaFlowDefinition definition = definitionMapper.selectById(instance.getDefinitionId());
        OaFlowFormVersion formVersion = formVersionMapper.selectById(instance.getFormVersionId());
        if (definition != null) {
            vo.setFormKey(definition.getFormKey());
        }
        if (formVersion != null) {
            vo.setFormName(formVersion.getFormName());
            vo.setFormVersionNo(formVersion.getVersionNo());
        }
        vo.setFormSchema(instance.getFormSchemaSnapshot());
        vo.setFormData(instance.getFormData());
        vo.setActions(selectHistory(instanceId));
        for (Task task : taskService.createTaskQuery().processInstanceId(instance.getFlowableProcInstId()).list()) {
            vo.getCurrentTasks().add(toTaskVo(instance, task));
        }
        return vo;
    }

    @Override
    public List<TaskActionVo> selectHistory(Long instanceId) {
        OaFlowInstance instance = requireVisibleInstance(instanceId);
        return taskActionMapper.selectList(new LambdaQueryWrapper<OaFlowTaskAction>()
                .eq(OaFlowTaskAction::getInstanceId, instance.getId())
                .orderByAsc(OaFlowTaskAction::getActionTime))
            .stream().map(this::toActionVo).toList();
    }

    @Override
    public byte[] diagram(Long instanceId) {
        OaFlowInstance instance = requireVisibleInstance(instanceId);
        OaFlowDefinitionVersion version = versionMapper.selectById(instance.getDefinitionVersionId());
        if (version == null || version.getFlowableProcDefId() == null) {
            throw new ServiceException("流程版本尚未部署", 404);
        }
        try {
            List<String> highlight = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getFlowableProcInstId()).list().stream()
                .map(HistoricActivityInstance::getActivityId).distinct().toList();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(version.getFlowableProcDefId());
            String font = DiagramFontProvider.family();
            try (InputStream in = new DefaultProcessDiagramGenerator()
                .generateDiagram(bpmnModel, "png", highlight, java.util.Collections.emptyList(),
                    font, font, font, null, 1.0, false);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw WorkflowOrchestrator.translate(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo revoke(Long instanceId) {
        Long userId = LoginHelper.getUserId();
        OaFlowInstance instance = requireVisibleInstance(instanceId);
        boolean anyAction = taskActionMapper.selectCount(new LambdaQueryWrapper<OaFlowTaskAction>()
            .eq(OaFlowTaskAction::getInstanceId, instance.getId())) > 0;
        boolean running = instance.getStatus() == FlowInstanceStatus.RUNNING.code();
        if (!WorkflowAccessPolicy.canRevoke(LoginHelper.isSuperAdmin(), userId, instance.getInitiatorUserId(), anyAction, running)) {
            throw new ServiceException(anyAction ? "WF_STATE_CONFLICT 已有人处理，不能撤销" : "WF_FORBIDDEN 无撤销权限", anyAction ? 409 : 403);
        }
        OaFlowTaskAction action = new OaFlowTaskAction();
        action.setInstanceId(instance.getId());
        action.setFlowableTaskId(null);
        action.setTaskName("撤销流程");
        action.setAction("cancel");
        action.setOperatorUserId(userId);
        action.setComment("发起人撤销");
        action.setActionTime(new java.util.Date());
        taskActionMapper.insert(action);
        orchestrator.revokeInstance(instance.getId(), userId);
        return toInstanceVo(instanceMapper.selectById(instanceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo suspend(Long instanceId) {
        Long userId = LoginHelper.getUserId();
        requireMonitorAction(instanceId, "wf:instance:suspend", "挂起");
        writeInstanceAction(instanceId, "suspend", userId, "挂起流程");
        orchestrator.suspendInstance(instanceId, userId);
        return toInstanceVo(instanceMapper.selectById(instanceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo resume(Long instanceId) {
        Long userId = LoginHelper.getUserId();
        requireMonitorAction(instanceId, "wf:instance:resume", "恢复");
        writeInstanceAction(instanceId, "resume", userId, "恢复流程");
        orchestrator.resumeInstance(instanceId, userId);
        return toInstanceVo(instanceMapper.selectById(instanceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstanceVo terminate(Long instanceId, String reason) {
        Long userId = LoginHelper.getUserId();
        requireMonitorAction(instanceId, "wf:instance:terminate", "终止");
        writeInstanceAction(instanceId, "terminate", userId,
            reason == null || reason.isBlank() ? "管理员强制终止" : "管理员强制终止：" + reason);
        orchestrator.terminateInstance(instanceId, userId, reason);
        outboxWriter.writeSystem("FTERM-" + instanceId, instanceMapper.selectById(instanceId).getInitiatorUserId(),
            "您的流程已被强制终止", instanceMapper.selectById(instanceId).getTitle());
        return toInstanceVo(instanceMapper.selectById(instanceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanTimeouts(int hours) {
        long threshold = System.currentTimeMillis() - Math.max(1, hours) * 3600_000L;
        java.util.Date cutoff = new java.util.Date(threshold);
        List<OaFlowInstance> running = instanceMapper.selectList(new LambdaQueryWrapper<OaFlowInstance>()
            .eq(OaFlowInstance::getStatus, FlowInstanceStatus.RUNNING.code())
            .eq(OaFlowInstance::getIsTimeout, 0)
            .lt(OaFlowInstance::getStartTime, cutoff));
        int notified = 0;
        for (OaFlowInstance instance : running) {
            List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableProcInstId()).list();
            boolean stale = tasks.stream().allMatch(t -> t.getCreateTime() != null && t.getCreateTime().before(cutoff));
            if (tasks.isEmpty() || !stale) {
                continue;
            }
            instance.setIsTimeout(1);
            instanceMapper.updateById(instance);
            String day = java.time.LocalDate.now().toString();
            for (Task task : tasks) {
                for (Long userId : orchestrator.assigneesOf(task)) {
                    try {
                        outboxWriter.writeSystem("FTIMEOUT-" + instance.getId() + "-" + task.getId() + "-" + userId + "-" + day,
                            userId, "流程待办已超时，请尽快处理", instance.getTitle());
                        notified++;
                    } catch (org.springframework.dao.DuplicateKeyException ignored) {
                        // 每任务每人每天只提醒一次
                    }
                }
            }
        }
        return notified;
    }

    private void requireMonitorAction(Long instanceId, String permission, String action) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        if (!LoginHelper.isSuperAdmin() && !hasMonitorScope()) {
            throw new ServiceException("WF_FORBIDDEN 无权" + action + "流程", 403);
        }
    }

    private void writeInstanceAction(Long instanceId, String actionKey, Long userId, String comment) {
        OaFlowTaskAction action = new OaFlowTaskAction();
        action.setInstanceId(instanceId);
        action.setTaskName(actionKey);
        action.setAction(actionKey);
        action.setOperatorUserId(userId);
        action.setComment(comment);
        action.setActionTime(new java.util.Date());
        taskActionMapper.insert(action);
    }

    private OaFlowInstance requireVisibleInstance(Long instanceId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在或对当前用户不可见", 404);
        }
        Long userId = LoginHelper.getUserId();
        boolean participant = taskActionMapper.selectCount(new LambdaQueryWrapper<OaFlowTaskAction>()
                .eq(OaFlowTaskAction::getInstanceId, instanceId)
                .eq(OaFlowTaskAction::getOperatorUserId, userId)) > 0
            || isCurrentParticipant(instance, userId);
        if (!WorkflowAccessPolicy.canViewInstance(LoginHelper.isSuperAdmin(), hasMonitorScope(),
            userId, instance.getInitiatorUserId(), participant)) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在或对当前用户不可见", 404);
        }
        return instance;
    }

    private boolean isCurrentParticipant(OaFlowInstance instance, Long userId) {
        for (Task task : taskService.createTaskQuery().processInstanceId(instance.getFlowableProcInstId()).list()) {
            if (orchestrator.assigneesOf(task).contains(userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMonitorScope() {
        try {
            var loginUser = LoginHelper.getLoginUser();
            return loginUser != null && loginUser.getMenuPermission() != null
                && loginUser.getMenuPermission().contains("wf:instance:list");
        } catch (Exception e) {
            return false;
        }
    }

    private InstanceVo toInstanceVo(OaFlowInstance instance) {
        InstanceVo vo = new InstanceVo();
        copy(instanceVoOf(instance), vo);
        return vo;
    }

    private InstanceVo instanceVoOf(OaFlowInstance instance) {
        InstanceVo vo = new InstanceVo();
        vo.setId(instance.getId());
        OaFlowDefinition definition = definitionMapper.selectById(instance.getDefinitionId());
        OaFlowDefinitionVersion version = versionMapper.selectById(instance.getDefinitionVersionId());
        if (definition != null) {
            vo.setProcessKey(definition.getProcessKey());
            vo.setProcessName(definition.getProcessName());
        }
        if (version != null) {
            vo.setDefinitionVersionNo(version.getVersionNo());
        }
        vo.setBusinessType(instance.getBusinessType());
        vo.setBusinessId(instance.getBusinessId());
        vo.setBusinessKey(instance.getBusinessKey());
        vo.setSubmissionNo(instance.getSubmissionNo());
        vo.setTitle(instance.getTitle());
        vo.setInitiatorUserId(instance.getInitiatorUserId());
        vo.setInitiatorName(instance.getInitiatorName());
        vo.setPriority(instance.getPriority());
        vo.setStatus(instance.getStatus());
        vo.setCurrentTaskName(instance.getCurrentTaskName());
        vo.setCurrentAssignees(instance.getCurrentAssignees());
        vo.setStartTime(instance.getStartTime());
        vo.setEndTime(instance.getEndTime());
        vo.setDuration(instance.getDuration());
        vo.setLockVersion(instance.getLockVersion());
        return vo;
    }

    private void copy(InstanceVo source, InstanceVo target) {
        target.setId(source.getId());
        target.setProcessKey(source.getProcessKey());
        target.setProcessName(source.getProcessName());
        target.setDefinitionVersionNo(source.getDefinitionVersionNo());
        target.setBusinessType(source.getBusinessType());
        target.setBusinessId(source.getBusinessId());
        target.setBusinessKey(source.getBusinessKey());
        target.setSubmissionNo(source.getSubmissionNo());
        target.setTitle(source.getTitle());
        target.setInitiatorUserId(source.getInitiatorUserId());
        target.setInitiatorName(source.getInitiatorName());
        target.setPriority(source.getPriority());
        target.setStatus(source.getStatus());
        target.setCurrentTaskName(source.getCurrentTaskName());
        target.setCurrentAssignees(source.getCurrentAssignees());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setDuration(source.getDuration());
        target.setLockVersion(source.getLockVersion());
    }

    private TaskVo toTaskVo(OaFlowInstance instance, Task task) {
        TaskVo vo = new TaskVo();
        vo.setTaskId(task.getId());
        vo.setInstanceId(instance.getId());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setTaskName(task.getName());
        vo.setTaskDefKey(task.getTaskDefinitionKey());
        if (task.getAssignee() != null) {
            vo.setAssigneeId(Long.valueOf(task.getAssignee()));
        }
        vo.setBusinessType(instance.getBusinessType());
        vo.setBusinessId(instance.getBusinessId());
        vo.setTitle(instance.getTitle());
        vo.setInstanceStatus(instance.getStatus());
        vo.setInitiatorName(instance.getInitiatorName());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }

    private TaskActionVo toActionVo(OaFlowTaskAction action) {
        TaskActionVo vo = new TaskActionVo();
        vo.setTaskName(action.getTaskName());
        vo.setAction(action.getAction());
        vo.setOperatorUserId(action.getOperatorUserId());
        vo.setOperatorName(action.getOperatorName());
        vo.setOldAssigneeName(action.getOldAssigneeName());
        vo.setNewAssigneeName(action.getNewAssigneeName());
        vo.setComment(action.getComment());
        vo.setActionTime(action.getActionTime());
        return vo;
    }
}
