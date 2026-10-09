package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.InstanceCcBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.bo.TaskAddsignBo;
import org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo;
import org.dromara.agentoa.workflow.domain.bo.TaskReturnBo;
import org.dromara.agentoa.workflow.domain.bo.TaskTransferBo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.CcVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;

public interface IWorkflowTaskService {

    PageVo<TaskVo> selectTodo(PageQuery page);

    PageVo<TaskVo> selectDone(PageQuery page);

    TaskVo selectTask(String taskId);

    /** 幂等重放：按任务 ID 返回办理结果（含已结束任务） */
    TaskVo selectTaskResult(String flowableTaskId);

    TaskVo complete(String taskId, TaskCompleteBo bo);

    TaskVo transfer(String taskId, TaskTransferBo bo);

    /** 退回（P1）：退回到发起节点或历史节点 */
    TaskVo returnTask(String taskId, TaskReturnBo bo);

    /** 加签（P1）：before 前加签为共同办理人；after 后加签按顺序续办 */
    TaskVo addsign(String taskId, TaskAddsignBo bo);

    /** 催办（P1）：发起人催办当前节点，站内信提醒，不改变任务状态 */
    void urge(String taskId, String comment);

    /** 抄送我的（P1，API 规范 4.4 /tasks/cc） */
    PageVo<CcVo> selectCc(PageQuery page);

    /** 发起抄送（P1）：向指定用户抄送流程 */
    void cc(InstanceCcBo bo, Long instanceId);
}
