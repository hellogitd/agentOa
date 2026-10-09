package org.dromara.agentoa.workflow.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.assigner.AssigneeResolutionException;
import org.dromara.agentoa.workflow.domain.OaFlowBusinessRef;
import org.dromara.agentoa.workflow.domain.OaFlowDefinition;
import org.dromara.agentoa.workflow.domain.OaFlowDefinitionVersion;
import org.dromara.agentoa.workflow.domain.OaFlowFormVersion;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.enums.FlowInstanceStatus;
import org.dromara.agentoa.workflow.domain.enums.FlowTemplateStatus;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.mapper.OaFlowBusinessRefMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程编排事务（docs/12）：业务记录、指定版本实例、业务关联与 outbox 同事务提交，失败全部回滚。
 */
@Component
@RequiredArgsConstructor
public class WorkflowOrchestrator {

    private final FlowHandlerRegistry handlerRegistry;
    private final FlowEventListenerRegistry eventListenerRegistry;
    private final OaFlowDefinitionMapper definitionMapper;
    private final OaFlowDefinitionVersionMapper versionMapper;
    private final OaFlowFormVersionMapper formVersionMapper;
    private final OaFlowInstanceMapper instanceMapper;
    private final OaFlowBusinessRefMapper businessRefMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final OutboxWriter outboxWriter;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final MiCollectionResolver miCollectionResolver;
    private final CcMaterializer ccMaterializer;
    private final UserSelectResolver userSelectResolver;

    @Transactional(rollbackFor = Exception.class)
    public InstanceStartVo start(BusinessType type, Long businessId, Integer lockVersion, Long initiatorUserId,
                                 String titleOverride, Integer priority) {
        return start(type.key(), null, businessId, lockVersion, initiatorUserId, titleOverride, priority, null);
    }

    /**
     * 发起流程：按「业务类型标识 + 可选定义 ID」定位流程定义。
     * 纯 OA 表单（M5 通用承接）必须显式传 definitionId，避免同业务类型下多定义歧义。
     */
    @Transactional(rollbackFor = Exception.class)
    public InstanceStartVo start(String businessTypeKey, Long definitionId, Long businessId, Integer lockVersion,
                                 Long initiatorUserId, String titleOverride, Integer priority) {
        return start(businessTypeKey, definitionId, businessId, lockVersion, initiatorUserId, titleOverride, priority, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public InstanceStartVo start(BusinessType type, Long businessId, Integer lockVersion, Long initiatorUserId,
                                 String titleOverride, Integer priority, Map<String, Object> userSelects) {
        return start(type.key(), null, businessId, lockVersion, initiatorUserId, titleOverride, priority, userSelects);
    }

    /**
     * @param userSelects 发起人自选审批人（nodeId → userId / userId[]）：只映射到链上 USER_SELECT
     *                    节点的专用变量 {@code userSelect_<nodeId>}，其余键直接拒绝（防变量注入）
     */
    @Transactional(rollbackFor = Exception.class)
    public InstanceStartVo start(String businessTypeKey, Long definitionId, Long businessId, Integer lockVersion,
                                 Long initiatorUserId, String titleOverride, Integer priority,
                                 Map<String, Object> userSelects) {
        if (initiatorUserId == null) {
            throw new ServiceException("未获取到当前登录用户", 401);
        }
        if (businessTypeKey == null || businessTypeKey.isBlank()) {
            throw new ServiceException("业务类型不能为空", 400);
        }
        FlowBusinessHandler handler = handlerRegistry.get(businessTypeKey);
        handler.validateForSubmit(businessId, initiatorUserId, lockVersion);
        eventListenerRegistry.notifyValidated(BusinessType.from(businessTypeKey), businessId, initiatorUserId);

        OaFlowDefinition definition = resolveDefinition(businessTypeKey, definitionId);
        OaFlowDefinitionVersion version = versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definition.getId())
            .eq(OaFlowDefinitionVersion::getVersionNo, definition.getCurrentVersionNo()));
        if (version == null || !FlowTemplateStatus.PUBLISHED.name().equals(version.getStatus())
            || version.getFlowableProcDefId() == null) {
            throw new ServiceException("流程模板版本未发布", 409);
        }
        OaFlowFormVersion formVersion = formVersionMapper.selectOne(new LambdaQueryWrapper<OaFlowFormVersion>()
            .eq(OaFlowFormVersion::getFormKey, definition.getFormKey())
            .eq(OaFlowFormVersion::getStatus, FlowTemplateStatus.PUBLISHED.name())
            .orderByDesc(OaFlowFormVersion::getVersionNo)
            .last("LIMIT 1"));
        if (formVersion == null) {
            throw new ServiceException("流程表单版本未发布", 409);
        }

        long existing = instanceMapper.selectCount(new LambdaQueryWrapper<OaFlowInstance>()
            .eq(OaFlowInstance::getBusinessType, definition.getBusinessType())
            .eq(OaFlowInstance::getBusinessId, businessId));
        int submissionNo = (int) existing + 1;
        String businessKey = definition.getBusinessType() + ":" + businessId + ":" + submissionNo;

        OaFlowInstance instance = new OaFlowInstance();
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionVersionId(version.getId());
        instance.setFormVersionId(formVersion.getId());
        instance.setFormSchemaSnapshot(formVersion.getSchemaJson());
        instance.setFormData(handler.formDataJson(businessId));
        instance.setBusinessType(definition.getBusinessType());
        instance.setBusinessId(businessId);
        instance.setBusinessKey(businessKey);
        instance.setSubmissionNo(submissionNo);
        instance.setTitle(titleOverride != null && !titleOverride.isBlank() ? titleOverride : handler.title(businessId));
        instance.setInitiatorUserId(initiatorUserId);
        instance.setInitiatorName(identityMapper.selectNickName(initiatorUserId));
        instance.setInitiatorDeptId(initiatorDeptId(initiatorUserId));
        instance.setPriority(priority == null ? 0 : priority);
        instance.setStatus(FlowInstanceStatus.RUNNING.code());
        instance.setStartTime(new Date());
        instance.setLockVersion(0);
        instanceMapper.insert(instance);

        OaFlowBusinessRef ref = new OaFlowBusinessRef();
        ref.setBusinessType(definition.getBusinessType());
        ref.setBusinessId(businessId);
        ref.setInstanceId(instance.getId());
        ref.setSubmissionNo(submissionNo);
        ref.setCreateTime(new Date());
        businessRefMapper.insert(ref);

        Map<String, Object> variables = new HashMap<>(handler.flowVariables(businessId));
        variables.put("initiatorUserId", initiatorUserId);
        variables.put("initiatorDeptId", instance.getInitiatorDeptId());
        byte[] bpmnBytes = BpmnSource.bytes(version);
        String bpmnXml = new String(bpmnBytes, java.nio.charset.StandardCharsets.UTF_8);
        // 发起人自选（USER_SELECT）：只写链上自选节点的专用变量，userId 做存在性/启用校验，失败整体回滚 400
        variables.putAll(userSelectResolver.toVariables(version.getChainJson(), userSelects));
        // 多实例集合（WF-08）：ROLE:key/WHITELIST 候选集或 USER_SELECT 自选集注入流程变量，解析失败整体回滚 400
        variables.putAll(miCollectionResolver.resolveCollections(bpmnBytes, initiatorUserId,
            instance.getInitiatorDeptId(), variables));
        // 条件分支变量：按受控 BPMN 里的金额阈值表达式一次性求值
        ConditionVariableResolver.enrich(variables, bpmnXml);
        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceById(version.getFlowableProcDefId(), businessKey, variables);
        } catch (Throwable e) {
            throw translate(e);
        }

        instance.setFlowableProcInstId(processInstance.getId());
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        applyTaskSnapshot(instance, tasks);
        instanceMapper.updateById(instance);

        // 审批链抄送节点：发起时生成抄送记录
        ccMaterializer.materialize(instance, version.getChainJson());

        handler.onStarted(businessId, instance.getId(), submissionNo);
        eventListenerRegistry.notifyStarted(BusinessType.from(businessTypeKey), businessId, instance.getId(), submissionNo);

        for (Task task : tasks) {
            for (Long userId : assigneesOf(task)) {
                outboxWriter.writeTodo("FTODO-" + instance.getId() + "-" + task.getId() + "-" + userId, userId,
                    "您有一条新的待办", instance.getTitle(), "flow_instance", instance.getId(),
                    "/flow/todo/" + instance.getId());
            }
        }

        InstanceStartVo vo = new InstanceStartVo();
        vo.setInstanceId(instance.getId());
        vo.setProcessInstanceId(processInstance.getId());
        vo.setBusinessKey(businessKey);
        for (Task task : tasks) {
            InstanceStartVo.CurrentTaskVo taskVo = new InstanceStartVo.CurrentTaskVo();
            taskVo.setTaskId(task.getId());
            taskVo.setTaskName(task.getName());
            if (task.getAssignee() != null) {
                taskVo.setAssigneeId(Long.valueOf(task.getAssignee()));
                taskVo.setAssigneeName(identityMapper.selectNickName(Long.valueOf(task.getAssignee())));
            }
            vo.getCurrentTasks().add(taskVo);
        }
        return vo;
    }

    /** 流程实例结束（全部用户任务完成）时的公共收尾 */
    @Transactional(rollbackFor = Exception.class)
    public void approveFinished(Long instanceId, Long operatorUserId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        instance.setStatus(FlowInstanceStatus.APPROVED.code());
        instance.setEndTime(new Date());
        if (instance.getStartTime() != null) {
            instance.setDuration(instance.getEndTime().getTime() - instance.getStartTime().getTime());
        }
        instance.setCurrentTaskName(null);
        instance.setCurrentAssignees(null);
        instanceMapper.updateById(instance);
        FlowBusinessHandler handler = handlerRegistry.get(instance.getBusinessType());
        handler.onApproved(instance.getBusinessId(), instance.getId(), instance.getFlowableProcInstId(), operatorUserId);
        eventListenerRegistry.notifyApproved(BusinessType.from(instance.getBusinessType()), instance.getBusinessId(),
            instance.getId(), operatorUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectInstance(Long instanceId, Long operatorUserId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        try {
            runtimeService.deleteProcessInstance(instance.getFlowableProcInstId(), "rejected");
        } catch (Throwable e) {
            throw translate(e);
        }
        instance.setStatus(FlowInstanceStatus.REJECTED.code());
        instance.setEndTime(new Date());
        instance.setCurrentTaskName(null);
        instance.setCurrentAssignees(null);
        instanceMapper.updateById(instance);
        FlowBusinessHandler handler = handlerRegistry.get(instance.getBusinessType());
        handler.onRejected(instance.getBusinessId(), instance.getId(), operatorUserId);
        eventListenerRegistry.notifyRejected(BusinessType.from(instance.getBusinessType()), instance.getBusinessId(),
            instance.getId(), operatorUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeInstance(Long instanceId, Long operatorUserId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        try {
            runtimeService.deleteProcessInstance(instance.getFlowableProcInstId(), "revoked");
        } catch (Throwable e) {
            throw translate(e);
        }
        instance.setStatus(FlowInstanceStatus.REVOKED.code());
        instance.setEndTime(new Date());
        instance.setCurrentTaskName(null);
        instance.setCurrentAssignees(null);
        instanceMapper.updateById(instance);
        FlowBusinessHandler handler = handlerRegistry.get(instance.getBusinessType());
        handler.onRevoked(instance.getBusinessId(), instance.getId(), operatorUserId);
        eventListenerRegistry.notifyRevoked(BusinessType.from(instance.getBusinessType()), instance.getBusinessId(),
            instance.getId(), operatorUserId);
    }

    /** 挂起（P1）：引擎与业务状态同步置 5，任务保持在途但不可办理。 */
    @Transactional(rollbackFor = Exception.class)
    public void suspendInstance(Long instanceId, Long operatorUserId) {
        OaFlowInstance instance = requireRunnable(instanceId, FlowInstanceStatus.SUSPENDED, "挂起");
        try {
            runtimeService.suspendProcessInstanceById(instance.getFlowableProcInstId());
        } catch (Throwable e) {
            throw translate(e);
        }
        instance.setStatus(FlowInstanceStatus.SUSPENDED.code());
        instance.setLockVersion(instance.getLockVersion() + 1);
        instanceMapper.updateById(instance);
    }

    /** 恢复（P1）：5 -> 1，条件更新保证只生效一次。 */
    @Transactional(rollbackFor = Exception.class)
    public void resumeInstance(Long instanceId, Long operatorUserId) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        if (instance.getStatus() == FlowInstanceStatus.RUNNING.code()) {
            return;
        }
        if (instance.getStatus() != FlowInstanceStatus.SUSPENDED.code()) {
            throw new ServiceException("WF_STATE_CONFLICT 流程状态已改变", 409);
        }
        try {
            runtimeService.activateProcessInstanceById(instance.getFlowableProcInstId());
        } catch (Throwable e) {
            throw translate(e);
        }
        instance.setStatus(FlowInstanceStatus.RUNNING.code());
        instance.setLockVersion(instance.getLockVersion() + 1);
        instanceMapper.updateById(instance);
    }

    /** 强制终止（P1，WF-14）：业务侧走 onTerminated（默认归撤销态）并保留终止原因。 */
    @Transactional(rollbackFor = Exception.class)
    public void terminateInstance(Long instanceId, Long operatorUserId, String reason) {
        OaFlowInstance instance = requireRunnable(instanceId, FlowInstanceStatus.TERMINATED, "终止");
        try {
            runtimeService.deleteProcessInstance(instance.getFlowableProcInstId(),
                reason == null || reason.isBlank() ? "terminated" : "terminated: " + reason);
        } catch (Throwable e) {
            throw translate(e);
        }
        instance.setStatus(FlowInstanceStatus.TERMINATED.code());
        instance.setEndTime(new Date());
        instance.setCurrentTaskName(null);
        instance.setCurrentAssignees(null);
        instanceMapper.updateById(instance);
        FlowBusinessHandler handler = handlerRegistry.get(instance.getBusinessType());
        handler.onTerminated(instance.getBusinessId(), instance.getId(), operatorUserId);
    }

    private OaFlowInstance requireRunnable(Long instanceId, FlowInstanceStatus target, String action) {
        OaFlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("WF_NOT_FOUND 流程不存在", 404);
        }
        if (instance.getStatus() == FlowInstanceStatus.SUSPENDED.code() && target == FlowInstanceStatus.SUSPENDED) {
            throw new ServiceException("WF_STATE_CONFLICT 流程已挂起", 409);
        }
        if (instance.getStatus() != FlowInstanceStatus.RUNNING.code()) {
            throw new ServiceException("WF_STATE_CONFLICT 流程状态已改变，不能" + action, 409);
        }
        return instance;
    }

    public void applyTaskSnapshot(OaFlowInstance instance, List<Task> tasks) {
        if (tasks.isEmpty()) {
            instance.setCurrentTaskName(null);
            instance.setCurrentAssignees(null);
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> assignees = new ArrayList<>();
        for (Task task : tasks) {
            names.add(task.getName());
            for (Long userId : assigneesOf(task)) {
                assignees.add(String.valueOf(userId));
            }
        }
        instance.setCurrentTaskName(String.join(",", names));
        String joinedAssignees = String.join(",", assignees);
        instance.setCurrentAssignees(joinedAssignees.substring(0, Math.min(joinedAssignees.length(), 500)));
    }

    public List<Long> assigneesOf(Task task) {
        List<Long> users = new ArrayList<>();
        if (task.getAssignee() != null) {
            users.add(Long.valueOf(task.getAssignee()));
            return users;
        }
        taskService.getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
            .forEach(link -> users.add(Long.valueOf(link.getUserId())));
        return users;
    }

    /**
     * 定位流程定义：显式 definitionId 优先（纯 OA 表单/通用承接）；
     * 否则按业务类型标识取唯一已发布定义，命中多个时强制要求指定 definitionId。
     */
    private OaFlowDefinition resolveDefinition(String businessTypeKey, Long definitionId) {
        if (definitionId != null) {
            OaFlowDefinition definition = definitionMapper.selectById(definitionId);
            if (definition == null) {
                throw new ServiceException("WF_NOT_FOUND 流程定义不存在", 404);
            }
            if (!FlowTemplateStatus.PUBLISHED.name().equals(definition.getStatus())) {
                throw new ServiceException("WF_STATE_CONFLICT 流程定义未启用", 409);
            }
            return definition;
        }
        List<OaFlowDefinition> definitions = definitionMapper.selectList(new LambdaQueryWrapper<OaFlowDefinition>()
            .eq(OaFlowDefinition::getBusinessType, businessTypeKey)
            .eq(OaFlowDefinition::getStatus, FlowTemplateStatus.PUBLISHED.name()));
        if (definitions.isEmpty()) {
            throw new ServiceException("业务类型 " + businessTypeKey + " 没有已发布的流程模板", 409);
        }
        if (definitions.size() > 1) {
            throw new ServiceException("业务类型 " + businessTypeKey + " 存在多个已发布流程定义，请指定 definitionId", 409);
        }
        return definitions.get(0);
    }

    private Long initiatorDeptId(Long userId) {
        Long deptId = identityMapper.selectEmployeeDeptId(userId);
        return deptId != null ? deptId : identityMapper.selectUserDeptId(userId);
    }

    public static ServiceException translate(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof AssigneeResolutionException are) {
                return new ServiceException(are.getMessage(), 400);
            }
            if (current instanceof ServiceException se) {
                return se;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return new ServiceException("流程引擎执行失败: " + e.getMessage(), 500);
    }
}
