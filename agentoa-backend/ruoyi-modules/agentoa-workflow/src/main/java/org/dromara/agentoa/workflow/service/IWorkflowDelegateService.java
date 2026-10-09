package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.DelegateBo;
import org.dromara.agentoa.workflow.domain.vo.DelegateVo;

import java.util.List;

/**
 * 委托代理服务（P1，需求 WF-12，API 规范 4.5）。
 * <p>
 * 对象级授权：用户只能维护自己的委托（管理员可代查）。
 */
public interface IWorkflowDelegateService {

    List<DelegateVo> selectDelegates(Long ownerId);

    DelegateVo selectDelegate(Long delegateId);

    DelegateVo createDelegate(DelegateBo bo);

    DelegateVo updateDelegate(Long delegateId, DelegateBo bo);

    void deleteDelegate(Long delegateId);
}
