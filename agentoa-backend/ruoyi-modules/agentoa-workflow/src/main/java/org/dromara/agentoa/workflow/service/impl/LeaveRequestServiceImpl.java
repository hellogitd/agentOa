package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaLeaveRequest;
import org.dromara.agentoa.workflow.domain.bo.LeaveRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.OaLeaveRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.ILeaveRequestService;
import org.dromara.agentoa.workflow.service.support.DurationCalculators;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.service.support.RequestSupport;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements ILeaveRequestService, FlowBusinessHandler {

    private final OaLeaveRequestMapper requestMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final FlowHandlerRegistry handlerRegistry;
    private final DurationCalculators durationCalculators;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.LEAVE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(LeaveRequestBo bo) {
        Long userId = LoginHelper.getUserId();
        OaLeaveRequest request = new OaLeaveRequest();
        request.setUserId(userId);
        request.setEmployeeId(identityMapper.selectEmployeeId(userId));
        request.setLeaveType(bo.getLeaveType());
        request.setStartTime(bo.getStartTime());
        request.setEndTime(bo.getEndTime());
        request.setDurationMinutes(durationCalculators.minutes(userId, bo.getStartTime(), bo.getEndTime()));
        request.setReason(bo.getReason());
        request.setStatus(BizRequestStatus.DRAFT.code());
        request.setSubmissionNo(0);
        request.setLockVersion(0);
        request.setRemark(bo.getRemark());
        requestMapper.insert(request);
        return toVo(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo updateDraft(Long requestId, LeaveRequestBo bo) {
        OaLeaveRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        RequestSupport.checkLockVersion(bo.getLockVersion(), request.getLockVersion());
        request.setLeaveType(bo.getLeaveType());
        request.setStartTime(bo.getStartTime());
        request.setEndTime(bo.getEndTime());
        request.setDurationMinutes(durationCalculators.minutes(request.getUserId(), bo.getStartTime(), bo.getEndTime()));
        request.setReason(bo.getReason());
        request.setRemark(bo.getRemark());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaLeaveRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaLeaveRequest> query = new LambdaQueryWrapper<OaLeaveRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaLeaveRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaLeaveRequest::getCreateTime);
        IPage<OaLeaveRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaLeaveRequest request = require(requestId);
        return orchestrator.start(BusinessType.LEAVE, requestId, lockVersion, request.getUserId(), null, null,
            assigneeSelections);
    }

    // ------------------------------------------------------------ FlowBusinessHandler

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        OaLeaveRequest request = require(businessId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkSubmittable(request.getStatus());
        RequestSupport.checkLockVersion(lockVersion, request.getLockVersion());
    }

    @Override
    public int lockVersion(Long businessId) {
        return require(businessId).getLockVersion();
    }

    @Override
    public Map<String, Object> flowVariables(Long businessId) {
        return Map.of();
    }

    @Override
    public String title(Long businessId) {
        OaLeaveRequest request = require(businessId);
        return identityMapper.selectNickName(request.getUserId()) + "的请假申请";
    }

    @Override
    public String formDataJson(Long businessId) {
        OaLeaveRequest request = require(businessId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("leaveType", request.getLeaveType());
        data.put("startTime", RequestSupport.format(request.getStartTime()));
        data.put("endTime", RequestSupport.format(request.getEndTime()));
        data.put("durationMinutes", request.getDurationMinutes());
        data.put("reason", request.getReason());
        return JsonUtils.toJsonString(data);
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaLeaveRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        OaLeaveRequest request = require(businessId);
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        OaLeaveRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        OaLeaveRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    // ------------------------------------------------------------ internal

    private OaLeaveRequest require(Long requestId) {
        OaLeaveRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private BusinessRequestVo toVo(OaLeaveRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        vo.setBusinessType(BusinessType.LEAVE.key());
        vo.setUserId(request.getUserId());
        vo.setEmployeeId(request.getEmployeeId());
        vo.setTitle(identityMapper.selectNickName(request.getUserId()) + "的请假申请");
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
