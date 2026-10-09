package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.InstanceStartBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.InstanceDetailVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.TaskActionVo;

import java.util.List;

public interface IWorkflowInstanceService {

    InstanceStartVo start(InstanceStartBo bo);

    /** 幂等重放：按实例 ID 重建首次发起结果 */
    InstanceStartVo selectStartResult(Long instanceId);

    PageVo<InstanceVo> selectPage(String scope, String businessType, PageQuery page);

    InstanceDetailVo selectDetail(Long instanceId);

    List<TaskActionVo> selectHistory(Long instanceId);

    byte[] diagram(Long instanceId);

    InstanceVo revoke(Long instanceId);

    /** 挂起（P1）：status 1 -> 5 */
    InstanceVo suspend(Long instanceId);

    /** 恢复（P1）：status 5 -> 1 */
    InstanceVo resume(Long instanceId);

    /** 强制终止（P1，WF-14）：status -> 6，业务侧归撤销态并保留原因 */
    InstanceVo terminate(Long instanceId, String reason);

    /** 超时扫描（P1，WF-10）：超过 hours 未处理的在途任务打标并提醒，返回本次提醒数 */
    int scanTimeouts(int hours);
}
