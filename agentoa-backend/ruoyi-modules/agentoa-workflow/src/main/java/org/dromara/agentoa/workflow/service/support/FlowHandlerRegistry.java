package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.domain.enums.BusinessType;
import org.dromara.agentoa.workflow.spi.FlowBusinessHandler;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 承接器注册表：各业务 handler 在 {@code @PostConstruct} 自注册，避免与编排器形成构造循环依赖。
 * <p>
 * 按「业务类型标识（String）」精确匹配；未命中时回落到 {@link #registerDefault} 注册的通用承接器
 * （M5 GenericFlowHandler），从而让纯 OA 表单/自定义流程无需为每个业务类型写一个 handler。
 */
@Component
public class FlowHandlerRegistry {

    private final Map<String, FlowBusinessHandler> handlers = new ConcurrentHashMap<>();

    private volatile FlowBusinessHandler defaultHandler;

    public synchronized void register(FlowBusinessHandler handler) {
        handlers.put(handler.type().key(), handler);
    }

    /** 注册通用兜底承接器（未显式注册的业务类型走它） */
    public synchronized void registerDefault(FlowBusinessHandler handler) {
        this.defaultHandler = handler;
    }

    public FlowBusinessHandler get(String businessTypeKey) {
        String key = businessTypeKey == null ? "" : businessTypeKey.trim();
        FlowBusinessHandler handler = handlers.get(key);
        if (handler == null) {
            handler = defaultHandler;
        }
        if (handler == null) {
            throw new ServiceException("业务类型未注册承接器: " + key, 400);
        }
        return handler;
    }

    public FlowBusinessHandler get(BusinessType type) {
        return get(type == null ? null : type.key());
    }

    /** 是否已为该业务类型显式注册专用承接器 */
    public boolean hasSpecific(String businessTypeKey) {
        return businessTypeKey != null && handlers.containsKey(businessTypeKey.trim());
    }
}
