package org.dromara.agentoa.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RegularizeFlowHandler implements FlowBusinessHandler {

    private final LifecycleRequestServiceImpl lifecycleService;
    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.REGULARIZE;
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        lifecycleService.validateForSubmit(businessId, lockVersion);
    }

    @Override
    public int lockVersion(Long businessId) {
        return lifecycleService.lockVersion(businessId);
    }

    @Override
    public Map<String, Object> flowVariables(Long businessId) {
        return lifecycleService.flowVariables(businessId);
    }

    @Override
    public String title(Long businessId) {
        return lifecycleService.title(businessId);
    }

    @Override
    public String formDataJson(Long businessId) {
        return lifecycleService.formDataJson(businessId);
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
        lifecycleService.onStarted(businessId, instanceId, submissionNo);
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
        lifecycleService.onApproved(businessId, instanceId, flowableProcInstId);
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
        lifecycleService.onRejected(businessId, instanceId);
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
        lifecycleService.onRevoked(businessId, instanceId);
    }
}
