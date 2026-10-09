package org.dromara.agentoa.workflow.spi;

import org.dromara.agentoa.workflow.domain.enums.BusinessType;

import java.util.Map;

/**
 * 业务承接回调（docs/12）：业务规则由业务模块拥有，流程模块只负责通用编排。
 */
public interface FlowBusinessHandler {

    BusinessType type();

    /** 提交前校验：状态可提交、对象归属、锁版本 */
    void validateForSubmit(Long businessId, Long submitterUserId, Integer lockVersion);

    int lockVersion(Long businessId);

    /** 引擎流程变量（仅服务端计算的白名单变量，如报销金额分支） */
    Map<String, Object> flowVariables(Long businessId);

    String title(Long businessId);

    /** 表单数据 JSON（写入实例快照） */
    String formDataJson(Long businessId);

    /** 启动成功后回填承接单（同事务） */
    void onStarted(Long businessId, Long instanceId, int submissionNo);

    void onApproved(Long businessId, Long instanceId, String flowableProcInstId, Long operatorUserId);

    void onRejected(Long businessId, Long instanceId, Long operatorUserId);

    void onRevoked(Long businessId, Long instanceId, Long operatorUserId);

    /**
     * 管理员强制终止（P1，需求 WF-14）：业务侧归为撤销态并保留终止原因（docs/02 13.2）。
     * 默认沿用 onRevoked 语义，业务模块可覆盖以记录终止原因。
     */
    default void onTerminated(Long businessId, Long instanceId, Long operatorUserId) {
        onRevoked(businessId, instanceId, operatorUserId);
    }
}
