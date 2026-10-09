package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaReimburseRequest;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.bo.ReimburseRequestBo;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.OaReimburseRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IReimburseRequestService;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.service.support.RequestSupport;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ReimburseRequestServiceImpl implements IReimburseRequestService, FlowBusinessHandler {

    private final OaReimburseRequestMapper requestMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.REIMBURSE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(ReimburseRequestBo bo) {
        Long userId = LoginHelper.getUserId();
        OaReimburseRequest request = new OaReimburseRequest();
        request.setUserId(userId);
        request.setEmployeeId(identityMapper.selectEmployeeId(userId));
        request.setReimburseType(bo.getReimburseType() == null ? "expense" : bo.getReimburseType());
        request.setCurrency(bo.getCurrency() == null ? "CNY" : bo.getCurrency());
        request.setPayMethod(bo.getPayMethod());
        request.setBudgetId(bo.getBudgetId());
        applyDetails(request, bo);
        request.setReason(bo.getReason());
        request.setStatus(BizRequestStatus.DRAFT.code());
        request.setSubmissionNo(0);
        request.setLockVersion(0);
        request.setRemark(bo.getRemark());
        request.setReimburseNo(generateNo());
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException e) {
            request.setReimburseNo(generateNo());
            requestMapper.insert(request);
        }
        return toVo(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo updateDraft(Long requestId, ReimburseRequestBo bo) {
        OaReimburseRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        RequestSupport.checkLockVersion(bo.getLockVersion(), request.getLockVersion());
        if (bo.getReimburseType() != null) {
            request.setReimburseType(bo.getReimburseType());
        }
        if (bo.getPayMethod() != null) {
            request.setPayMethod(bo.getPayMethod());
        }
        if (bo.getBudgetId() != null) {
            request.setBudgetId(bo.getBudgetId());
        }
        applyDetails(request, bo);
        request.setReason(bo.getReason());
        request.setRemark(bo.getRemark());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long requestId) {
        OaReimburseRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkEditable(request.getStatus());
        requestMapper.deleteById(requestId);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaReimburseRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaReimburseRequest> query = new LambdaQueryWrapper<OaReimburseRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaReimburseRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaReimburseRequest::getCreateTime);
        IPage<OaReimburseRequest> result = requestMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaReimburseRequest request = require(requestId);
        return orchestrator.start(BusinessType.REIMBURSE, requestId, lockVersion, request.getUserId(), null, null,
            assigneeSelections);
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        OaReimburseRequest request = require(businessId);
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
          OaReimburseRequest request = require(businessId);
          BigDecimal total = request.getTotalAmount();
          Map<String, Object> variables = new LinkedHashMap<>();
          // 金额源变量：受控条件分支按 amount_<op>_<阈值> 由 ConditionVariableResolver 统一求值
          if (total != null) {
            variables.put("amount", total);
          }
          variables.put("amount_le_1000", total != null && total.compareTo(new BigDecimal("1000.00")) <= 0);
          variables.put("amount_le_5000", total != null && total.compareTo(new BigDecimal("5000.00")) <= 0);
          variables.put("amount_gt_5000", total != null && total.compareTo(new BigDecimal("5000.00")) > 0);
          return variables;
    }

    @Override
    public String title(Long businessId) {
        OaReimburseRequest request = require(businessId);
        return identityMapper.selectNickName(request.getUserId()) + "的费用报销";
    }

    @Override
    public String formDataJson(Long businessId) {
        OaReimburseRequest request = require(businessId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reimburseType", request.getReimburseType());
        data.put("payMethod", request.getPayMethod());
        data.put("currency", request.getCurrency());
        data.put("totalAmount", request.getTotalAmount() == null ? "0.00" : request.getTotalAmount().toPlainString());
        data.put("details", request.getDetailsJson() == null ? List.of() : JsonUtils.parseArrayMap(request.getDetailsJson()));
        data.put("reason", request.getReason());
        return JsonUtils.toJsonString(data);
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaReimburseRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        OaReimburseRequest request = require(businessId);
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        OaReimburseRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        OaReimburseRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    // ------------------------------------------------------------ internal

    private void applyDetails(OaReimburseRequest request, ReimburseRequestBo bo) {
        if (bo.getDetails() == null || bo.getDetails().isEmpty()) {
            throw new ServiceException("报销明细不能为空", 400);
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> details = new ArrayList<>();
        for (ReimburseRequestBo.ReimburseDetailBo detail : bo.getDetails()) {
            if (detail.getAmount() == null || detail.getAmount().isBlank()) {
                throw new ServiceException("明细金额不能为空", 400);
            }
            BigDecimal amount = new BigDecimal(detail.getAmount());
            if (amount.signum() <= 0) {
                throw new ServiceException("明细金额必须大于 0", 400);
            }
            total = total.add(amount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("expenseType", detail.getExpenseType());
            row.put("expenseTypeId", detail.getExpenseTypeId());
            row.put("occurDate", detail.getOccurDate() == null ? null : detail.getOccurDate().toString());
            row.put("amount", amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
            row.put("invoiceNo", detail.getInvoiceNo());
            row.put("invoiceId", detail.getInvoiceId());
            row.put("description", detail.getDescription());
            details.add(row);
        }
        if (bo.getTotalAmount() != null && new BigDecimal(bo.getTotalAmount()).compareTo(total) != 0) {
            throw new ServiceException("明细总额必须等于主表总额", 400);
        }
        request.setDetailsJson(JsonUtils.toJsonString(details));
        request.setTotalAmount(total);
    }

    private String generateNo() {
        return "RB" + LocalDate.now().toString().replace("-", "")
            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private OaReimburseRequest require(Long requestId) {
        OaReimburseRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private BusinessRequestVo toVo(OaReimburseRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        vo.setBusinessType(BusinessType.REIMBURSE.key());
        vo.setUserId(request.getUserId());
        vo.setEmployeeId(request.getEmployeeId());
        vo.setTitle(identityMapper.selectNickName(request.getUserId()) + "的费用报销");
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
