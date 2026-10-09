package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaOvertimeRequest;
import org.dromara.agentoa.workflow.domain.bo.OvertimeRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.OaOvertimeRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IOvertimeRequestService;
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
public class OvertimeRequestServiceImpl implements IOvertimeRequestService, FlowBusinessHandler {

    private final OaOvertimeRequestMapper requestMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.OVERTIME;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(OvertimeRequestBo bo) {
        Long userId = LoginHelper.getUserId();
        OaOvertimeRequest request = new OaOvertimeRequest();
        request.setUserId(userId);
        request.setEmployeeId(identityMapper.selectEmployeeId(userId));
        request.setOvertimeDate(bo.getOvertimeDate());
        request.setOvertimeType(bo.getOvertimeType() == null ? "weekday" : bo.getOvertimeType());
        request.setStartTime(bo.getStartTime());
        request.setEndTime(bo.getEndTime());
        request.setDurationMinutes(RequestSupport.durationMinutes(bo.getStartTime(), bo.getEndTime()));
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
    public BusinessRequestVo updateDraft(Long requestId, OvertimeRequestBo bo) {
        OaOvertimeRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        RequestSupport.checkLockVersion(bo.getLockVersion(), request.getLockVersion());
        request.setOvertimeDate(bo.getOvertimeDate());
        if (bo.getOvertimeType() != null) {
            request.setOvertimeType(bo.getOvertimeType());
        }
        request.setStartTime(bo.getStartTime());
        request.setEndTime(bo.getEndTime());
        request.setDurationMinutes(RequestSupport.durationMinutes(bo.getStartTime(), bo.getEndTime()));
        request.setReason(bo.getReason());
        request.setRemark(bo.getRemark());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaOvertimeRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaOvertimeRequest> query = new LambdaQueryWrapper<OaOvertimeRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaOvertimeRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaOvertimeRequest::getCreateTime);
        IPage<OaOvertimeRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaOvertimeRequest request = require(requestId);
        return orchestrator.start(BusinessType.OVERTIME, requestId, lockVersion, request.getUserId(), null, null,
            assigneeSelections);
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        OaOvertimeRequest request = require(businessId);
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
        OaOvertimeRequest request = require(businessId);
        return identityMapper.selectNickName(request.getUserId()) + "的加班申请";
    }

    @Override
    public String formDataJson(Long businessId) {
        OaOvertimeRequest request = require(businessId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overtimeDate", RequestSupport.format(request.getOvertimeDate()));
        data.put("overtimeType", request.getOvertimeType());
        data.put("startTime", RequestSupport.format(request.getStartTime()));
        data.put("endTime", RequestSupport.format(request.getEndTime()));
        data.put("durationMinutes", request.getDurationMinutes());
        data.put("reason", request.getReason());
        return JsonUtils.toJsonString(data);
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaOvertimeRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        OaOvertimeRequest request = require(businessId);
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        OaOvertimeRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        OaOvertimeRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    private OaOvertimeRequest require(Long requestId) {
        OaOvertimeRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private BusinessRequestVo toVo(OaOvertimeRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        vo.setBusinessType(BusinessType.OVERTIME.key());
        vo.setUserId(request.getUserId());
        vo.setEmployeeId(request.getEmployeeId());
        vo.setTitle(identityMapper.selectNickName(request.getUserId()) + "的加班申请");
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
