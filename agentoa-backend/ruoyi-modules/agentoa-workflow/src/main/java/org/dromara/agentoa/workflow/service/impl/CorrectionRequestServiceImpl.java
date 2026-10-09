package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaCorrectionRequest;
import org.dromara.agentoa.workflow.domain.bo.CorrectionRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.OaCorrectionRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.ICorrectionRequestService;
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
public class CorrectionRequestServiceImpl implements ICorrectionRequestService, FlowBusinessHandler {

    private final OaCorrectionRequestMapper requestMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.CORRECTION;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(CorrectionRequestBo bo) {
        if (bo.getPunchType() != 1 && bo.getPunchType() != 2) {
            throw new ServiceException("时段取值非法", 400);
        }
        Long userId = LoginHelper.getUserId();
        OaCorrectionRequest request = new OaCorrectionRequest();
        request.setUserId(userId);
        request.setEmployeeId(identityMapper.selectEmployeeId(userId));
        request.setAttendanceDate(bo.getAttendanceDate());
        request.setPunchType(bo.getPunchType());
        request.setCorrectedTime(bo.getCorrectedTime());
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
    public BusinessRequestVo updateDraft(Long requestId, CorrectionRequestBo bo) {
        OaCorrectionRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        RequestSupport.checkLockVersion(bo.getLockVersion(), request.getLockVersion());
        if (bo.getPunchType() != 1 && bo.getPunchType() != 2) {
            throw new ServiceException("时段取值非法", 400);
        }
        request.setAttendanceDate(bo.getAttendanceDate());
        request.setPunchType(bo.getPunchType());
        request.setCorrectedTime(bo.getCorrectedTime());
        request.setReason(bo.getReason());
        request.setRemark(bo.getRemark());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaCorrectionRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaCorrectionRequest> query = new LambdaQueryWrapper<OaCorrectionRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaCorrectionRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaCorrectionRequest::getCreateTime);
        IPage<OaCorrectionRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaCorrectionRequest request = require(requestId);
        return orchestrator.start(BusinessType.CORRECTION, requestId, lockVersion, request.getUserId(), null, null,
            assigneeSelections);
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        OaCorrectionRequest request = require(businessId);
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
        OaCorrectionRequest request = require(businessId);
        return identityMapper.selectNickName(request.getUserId()) + "的补卡申请";
    }

    @Override
    public String formDataJson(Long businessId) {
        OaCorrectionRequest request = require(businessId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attendanceDate", RequestSupport.format(request.getAttendanceDate()));
        data.put("punchType", request.getPunchType());
        data.put("correctedTime", RequestSupport.format(request.getCorrectedTime()));
        data.put("reason", request.getReason());
        return JsonUtils.toJsonString(data);
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaCorrectionRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        OaCorrectionRequest request = require(businessId);
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        OaCorrectionRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        OaCorrectionRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    private OaCorrectionRequest require(Long requestId) {
        OaCorrectionRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private BusinessRequestVo toVo(OaCorrectionRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        vo.setBusinessType(BusinessType.CORRECTION.key());
        vo.setUserId(request.getUserId());
        vo.setEmployeeId(request.getEmployeeId());
        vo.setTitle(identityMapper.selectNickName(request.getUserId()) + "的补卡申请");
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
