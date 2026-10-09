package org.dromara.agentoa.workflow.spi;

import org.dromara.agentoa.workflow.domain.enums.BusinessType;

/**
 * 流程业务事件监听（docs/12 暂缓项转交模块 3/4）：业务模块注册后在承接单动作的同一事务内收到回调，
 * 用于额度冻结/结算/释放、发票占用等业务账本动作。通用流程服务不直接修改考勤或财务账本。
 */
public interface FlowEventListener {

    /** 提交前校验（编排事务内、承接器校验之后）：可抛出 ServiceException 阻断提交并整体回滚 */
    default void onBusinessValidated(BusinessType type, Long businessId, Long submitterUserId) {
    }

    /** 流程启动成功（同事务）：业务事件 ID 建议使用 businessType:businessId:submissionNo */
    default void onBusinessStarted(BusinessType type, Long businessId, Long instanceId, int submissionNo) {
    }

    default void onBusinessApproved(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
    }

    default void onBusinessRejected(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
    }

    default void onBusinessRevoked(BusinessType type, Long businessId, Long instanceId, Long operatorUserId) {
    }
}
