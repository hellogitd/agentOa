package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.spi.FlowEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 流程事件监听注册表：业务模块在 {@code @PostConstruct} 自注册，按注册顺序在同一事务内广播。
 */
@Component
public class FlowEventListenerRegistry {

    private final List<FlowEventListener> listeners = new CopyOnWriteArrayList<>();

    public synchronized void register(FlowEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void notifyValidated(BusinessType type, Long businessId, Long submitterUserId) {
        for (FlowEventListener listener : listeners) {
            listener.onBusinessValidated(type, businessId, submitterUserId);
        }
    }

    public void notifyStarted(BusinessType type, Long businessId, Long instanceId, int submissionNo) {
        for (FlowEventListener listener : listeners) {
            listener.onBusinessStarted(type, businessId, instanceId, submissionNo);
        }
    }

    public void notifyApproved(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        for (FlowEventListener listener : listeners) {
            listener.onBusinessApproved(type, businessId, instanceId, operatorUserId);
        }
    }

    public void notifyRejected(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        for (FlowEventListener listener : listeners) {
            listener.onBusinessRejected(type, businessId, instanceId, operatorUserId);
        }
    }

    public void notifyRevoked(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
        for (FlowEventListener listener : listeners) {
            listener.onBusinessRevoked(type, businessId, instanceId, operatorUserId);
        }
    }
}
