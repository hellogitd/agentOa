package org.dromara.agentoa.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.service.support.FlowHandlerRegistry;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 会签演示模板承接器（WF-08）：无业务表的通用审批演示（多实例全员同意语义）。
 */
@Component
@RequiredArgsConstructor
public class CountersignFlowHandler implements FlowBusinessHandler {

    private final FlowHandlerRegistry handlerRegistry;

    @jakarta.annotation.PostConstruct
    public void registerHandler() {
        handlerRegistry.register(this);
    }

    @Override
    public BusinessType type() {
        return BusinessType.COUNTERSIGN;
    }

    @Override
    public void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion) {
        if (businessId == null) {
            throw new ServiceException("演示流程需要业务ID", 400);
        }
    }

    @Override
    public int lockVersion(Long businessId) {
        return 0;
    }

    @Override
    public Map<String, Object> flowVariables(Long businessId) {
        return Map.of();
    }

    @Override
    public String title(Long businessId) {
        return "会签审批演示-" + businessId;
    }

    @Override
    public String formDataJson(Long businessId) {
        return "{}";
    }

    @Override
    public void onStarted(Long businessId, Long instanceId, int submissionNo) {
    }

    @Override
    public void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId) {
    }

    @Override
    public void onRejected(Long businessId, Long instanceId, Long operatorUserId) {
    }

    @Override
    public void onRevoked(Long businessId, Long instanceId, Long operatorUserId) {
    }
}
