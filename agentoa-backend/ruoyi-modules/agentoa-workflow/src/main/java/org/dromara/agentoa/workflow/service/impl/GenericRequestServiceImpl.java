package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.OaFlowDefinition;
import org.dromara.agentoa.workflow.domain.OaFlowGenericRequest;
import org.dromara.agentoa.workflow.domain.bo.GenericRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.enums.BizRequestStatus;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.domain.enums.FlowTemplateStatus;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowGenericRequestMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IGenericRequestService;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.service.support.PlainJson;
import org.dromara.agentoa.workflow.service.support.RequestSupport;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 OA 申请承接（M5）：既是申请单服务，也是所有未显式注册业务类型的兜底承接器。
 * <p>
 * 表单字段差异收敛在 {@code formData}（JSON）；流程结构由定义上的结构化审批链决定。
 * {@link #flowVariables} 会把表单里的 {@code amount} 提取为流程变量，
 * 让「金额 > 5 万加总经理」这类受控条件分支对纯 OA 表单同样生效。
 */
@Service
@RequiredArgsConstructor
public class GenericRequestServiceImpl implements IGenericRequestService, FlowBusinessHandler {

    private final OaFlowGenericRequestMapper requestMapper;
    private final OaFlowDefinitionMapper definitionMapper;
    private final WorkflowIdentityReadMapper identityMapper;
    private final WorkflowOrchestrator orchestrator;
    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.registerDefault(this);
    }

    // ------------------------------------------------------------ 申请单服务

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo createDraft(GenericRequestBo bo) {
        Long userId = LoginHelper.getUserId();
        OaFlowDefinition definition = requireDefinition(bo.getDefinitionId());
        OaFlowGenericRequest request = new OaFlowGenericRequest();
        request.setDefinitionId(definition.getId());
        request.setUserId(userId);
        request.setTitle(resolveTitle(bo.getTitle(), definition));
        request.setFormData(validateFormData(bo.getFormData()));
        request.setStatus(BizRequestStatus.DRAFT.code());
        request.setSubmissionNo(0);
        request.setLockVersion(0);
        request.setRemark(bo.getRemark());
        requestMapper.insert(request);
        return toVo(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo updateDraft(Long requestId, GenericRequestBo bo) {
        OaFlowGenericRequest request = require(requestId);
        RequestSupport.checkOwner(request.getUserId());
        RequestSupport.checkSubmittable(request.getStatus());
        if (bo.getTitle() != null && !bo.getTitle().isBlank()) {
            request.setTitle(bo.getTitle().trim());
        }
        if (bo.getFormData() != null) {
            request.setFormData(validateFormData(bo.getFormData()));
        }
        if (bo.getRemark() != null) {
            request.setRemark(bo.getRemark());
        }
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
        return toVo(request);
    }

    @Override
    public BusinessRequestVo selectRequest(Long requestId) {
        OaFlowGenericRequest request = require(requestId);
        if (!LoginHelper.isSuperAdmin() && !request.getUserId().equals(LoginHelper.getUserId())) {
            throw new ServiceException("WF_FORBIDDEN 无权查看该申请", 403);
        }
        return toVo(request);
    }

    @Override
    public PageVo<BusinessRequestVo> selectPage(PageQuery page) {
        LambdaQueryWrapper<OaFlowGenericRequest> query = new LambdaQueryWrapper<OaFlowGenericRequest>();
        if (!LoginHelper.isSuperAdmin()) {
            query.eq(OaFlowGenericRequest::getUserId, LoginHelper.getUserId());
        }
        query.orderByDesc(OaFlowGenericRequest::getCreateTime);
        IPage<OaFlowGenericRequest> result = requestMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), query);
        List<BusinessRequestVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections) {
        OaFlowGenericRequest request = require(requestId);
        OaFlowDefinition definition = requireDefinition(request.getDefinitionId());
        return orchestrator.start(definition.getBusinessType(), definition.getId(), requestId,
            lockVersion, request.getUserId(), request.getTitle(), null, assigneeSelections);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessRequestVo launch(GenericRequestBo bo) {
        BusinessRequestVo vo = createDraft(bo);
        submit(vo.getId(), vo.getLockVersion(), bo.getAssigneeSelections());
        return selectRequest(vo.getId());
    }

    // ------------------------------------------------------------ FlowBusinessHandler

    @Override
    public BusinessType type() {
        return BusinessType.GENERIC;
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        OaFlowGenericRequest request = require(businessId);
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
        OaFlowGenericRequest request = require(businessId);
        Map<String, Object> variables = new LinkedHashMap<>();
        BigDecimal amount = extractAmount(request.getFormData());
        if (amount != null) {
            variables.put("amount", amount);
        }
        return variables;
    }

    @Override
    public String title(Long businessId) {
        OaFlowGenericRequest request = require(businessId);
        return request.getTitle() != null && !request.getTitle().isBlank()
            ? request.getTitle()
            : identityMapper.selectNickName(request.getUserId()) + "的通用申请";
    }

    @Override
    public String formDataJson(Long businessId) {
        return require(businessId).getFormData();
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        OaFlowGenericRequest request = require(businessId);
        request.setStatus(BizRequestStatus.RUNNING.code());
        request.setFlowInstanceId(instanceId);
        request.setSubmissionNo(submissionNo);
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        OaFlowGenericRequest request = require(businessId);
        request.setStatus(BizRequestStatus.APPROVED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        OaFlowGenericRequest request = require(businessId);
        request.setStatus(BizRequestStatus.REJECTED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        OaFlowGenericRequest request = require(businessId);
        request.setStatus(BizRequestStatus.CANCELLED.code());
        request.setLockVersion(request.getLockVersion() + 1);
        requestMapper.updateById(request);
    }

    // ------------------------------------------------------------ internal

    private OaFlowGenericRequest require(Long requestId) {
        OaFlowGenericRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new ServiceException("WF_NOT_FOUND 申请不存在", 404);
        }
        return request;
    }

    private OaFlowDefinition requireDefinition(Long definitionId) {
        OaFlowDefinition definition = definitionId == null ? null : definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new ServiceException("WF_NOT_FOUND 流程定义不存在", 404);
        }
        return definition;
    }

    private String resolveTitle(String title, OaFlowDefinition definition) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return definition.getProcessName() + "-" + LoginHelper.getUserId();
    }

    /** 表单数据必须是 JSON 对象，且不携带可执行内容（只做结构校验，字段语义由表单 Schema 约束） */
    private String validateFormData(String formData) {
        if (formData == null || formData.isBlank()) {
            return "{}";
        }
        String trimmed = formData.trim();
        if (!org.dromara.common.json.utils.JsonUtils.isJsonObject(trimmed)) {
            throw new ServiceException("表单数据必须是 JSON 对象", 400);
        }
        return trimmed;
    }

    /** 从表单数据里提取金额（amount 字段），用于受控条件分支求值 */
    static BigDecimal extractAmount(String formData) {
        if (formData == null || formData.isBlank()) {
            return null;
        }
        Map<String, Object> map;
        try {
            map = PlainJson.mapper().readValue(formData,
                PlainJson.mapper().getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            return null;
        }
        Object raw = map == null ? null : map.get("amount");
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("表单金额字段 amount 不是合法数字: " + raw, 400);
        }
    }

    private BusinessRequestVo toVo(OaFlowGenericRequest request) {
        BusinessRequestVo vo = new BusinessRequestVo();
        vo.setId(request.getId());
        OaFlowDefinition definition = definitionMapper.selectById(request.getDefinitionId());
        vo.setBusinessType(definition == null ? BusinessType.GENERIC.key() : definition.getBusinessType());
        vo.setUserId(request.getUserId());
        vo.setTitle(request.getTitle());
        vo.setStatus(request.getStatus());
        vo.setFormData(request.getFormData());
        vo.setFlowInstanceId(request.getFlowInstanceId());
        vo.setSubmissionNo(request.getSubmissionNo());
        vo.setLockVersion(request.getLockVersion());
        vo.setCreateTime(request.getCreateTime());
        vo.setUpdateTime(request.getUpdateTime());
        return vo;
    }
}
