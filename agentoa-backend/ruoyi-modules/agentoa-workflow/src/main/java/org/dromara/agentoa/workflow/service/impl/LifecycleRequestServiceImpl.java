package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.OaOffboardBo;
import org.dromara.agentoa.hr.domain.bo.OaRegularizeBo;
import org.dromara.agentoa.hr.domain.bo.OaStatusBo;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.workflow.domain.OaLifecycleRequest;
import org.dromara.agentoa.workflow.domain.bo.LifecycleRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.OaLifecycleRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.ILifecycleRequestService;
import org.dromara.agentoa.workflow.service.support.RequestSupport;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 转正/离职申请承接单（docs/05 section 3.5/3.7）：审批通过后按生效日期驱动 HR 状态机。
 */
@Service
@RequiredArgsConstructor
public class LifecycleRequestServiceImpl implements ILifecycleRequestService {

    private final OaLifecycleRequestMapper requestMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final IHrEmployeeService employeeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(LifecycleRequestBo bo) {
        checkRequestType(bo.getRequestType());
        Long userId = LoginHelper.getUserId();
        Long employeeId = bo.getEmployeeId() != null ? bo.getEmployeeId() : identityMapper.selectEmployeeId(userId);
        if (employeeId == null) {
            throw new ServiceException("未找到员工档案，请指定 employeeId", 400);
        }
        OaLifecycleRequest request = new OaLifecycleRequest();
        request.setEmployeeId(employeeId);
        request.setUserId(userId);
        request.setRequestType(bo.getRequestType());
        request.setEffectiveDate(bo.getEffectiveDate());
        request.setFormData(normalizeFormData(bo.getFormData()));
        request.setStatus(BizRequestStatus.DRAFT.code());
        request.setSubmissionNo(0);
        request.setLockVersion(0);
        request.setRemark(bo.getRemark());
        requestMapper.insert(request);
        return toVo(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo updateDraft(Long requestId, LifecycleRequestBo bo) {
        OaLifecycleRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        RequestSupport.checkLockVersion(bo.getLockVersion(), request.getLockVersion());
        if (bo.getEffectiveDate() != null) {
            request.setEffectiveDate(bo.getEffectiveDate());
        }
        if (bo.getFormData() != null) {
            request.setFormData(normalizeFormData(bo.getFormData()));
        }
        request.setRemark(bo.getRemark());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaLifecycleRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaLifecycleRequest> query = new LambdaQueryWrapper<OaLifecycleRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaLifecycleRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaLifecycleRequest::getCreateTime);
        IPage<OaLifecycleRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaLifecycleRequest request = require(requestId);
        BusinessType type = BusinessType.from(request.getRequestType().toLowerCase());
        return orchestrator.start(type, requestId, lockVersion, request.getUserId(), null, null, assigneeSelections);
    }

    // ------------------------------------------------------------ 流程回调（由 Regularize/Offboard handler 转发）

    public void validateForSubmit(Long businessId, Integer lockVersion) {
        OaLifecycleRequest request = require(businessId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkSubmittable(request.getStatus());
        RequestSupport.checkLockVersion(lockVersion, request.getLockVersion());
    }

    public int lockVersion(Long businessId) {
        return require(businessId).getLockVersion();
    }

    public Map<String, Object> flowVariables(Long businessId) {
        return Map.of();
    }

    public String title(Long businessId) {
        OaLifecycleRequest request = require(businessId);
        String who = identityMapper.selectNickName(request.getUserId());
        return "REGULARIZE".equals(request.getRequestType()) ? who + "的转正申请" : who + "的离职申请";
    }

    public String formDataJson(Long businessId) {
        OaLifecycleRequest request = require(businessId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestType", request.getRequestType());
        data.put("effectiveDate", RequestSupport.format(request.getEffectiveDate()));
        if (request.getFormData() != null && !request.getFormData().isBlank()) {
            data.put("detail", JsonUtils.parseMap(request.getFormData()));
        }
        return JsonUtils.toJsonString(data);
    }

    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaLifecycleRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId) {
        OaLifecycleRequest request = require(businessId);
        if ("REGULARIZE".equals(request.getRequestType())) {
            OaRegularizeBo bo = new OaRegularizeBo();
            bo.setEmployeeId(request.getEmployeeId());
            bo.setRegularDate(request.getEffectiveDate());
            bo.setWorkflowInstanceId(flowableProcInstId);
            bo.setRemark("转正审批通过");
            employeeService.regularize(bo);
        } else {
            applyOffboard(request, flowableProcInstId);
        }
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    public void onRejected(Long businessId, Long instanceId) {
        OaLifecycleRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    public void onRevoked(Long businessId, Long instanceId) {
        OaLifecycleRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    private void applyOffboard(OaLifecycleRequest request, String flowableProcInstId) {
        if (request.getEffectiveDate() != null && request.getEffectiveDate().isAfter(LocalDate.now())) {
            OaStatusBo statusBo = new OaStatusBo();
            statusBo.setStatus("LEAVE_PENDING");
            statusBo.setReason("离职审批通过，待生效日 " + request.getEffectiveDate());
            employeeService.updateStatus(request.getEmployeeId(), statusBo);
            return;
        }
        OaOffboardBo bo = new OaOffboardBo();
        bo.setEmployeeId(request.getEmployeeId());
        bo.setReason(request.getFormData());
        bo.setLastWorkingDay(request.getEffectiveDate());
        bo.setWorkflowInstanceId(flowableProcInstId);
        employeeService.offboard(bo);
    }

    private void checkRequestType(String requestType) {
        if (!"REGULARIZE".equals(requestType) && !"OFFBOARD".equals(requestType)) {
            throw new ServiceException("申请类型取值非法", 400);
        }
    }

    private String normalizeFormData(String formData) {
        if (formData == null || formData.isBlank()) {
            return null;
        }
        if (!JsonUtils.isJsonObject(formData)) {
            throw new ServiceException("formData 必须是 JSON 对象", 400);
        }
        return formData;
    }

    private OaLifecycleRequest require(Long requestId) {
        OaLifecycleRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private BusinessRequestVo toVo(OaLifecycleRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        vo.setBusinessType(request.getRequestType().toLowerCase());
        vo.setUserId(request.getUserId());
        vo.setEmployeeId(request.getEmployeeId());
        vo.setTitle(title(request.getId()));
        vo.setStatus(request.getStatus());
        vo.setFormData(formDataJson(request.getId()));
        vo.setFlowInstanceId(request.getFlowInstanceId());
        vo.setSubmissionNo(request.getSubmissionNo());
        vo.setLockVersion(request.getLockVersion());
        vo.setCreateTime(request.getCreateTime());
        vo.setUpdateTime(request.getUpdateTime());
        return vo;
    }
}
