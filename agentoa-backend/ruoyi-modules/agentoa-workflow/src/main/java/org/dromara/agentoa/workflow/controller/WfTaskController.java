package org.dromara.agentoa.workflow.controller;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.bo.TaskAddsignBo;
import org.dromara.agentoa.workflow.domain.bo.TaskCompleteBo;
import org.dromara.agentoa.workflow.domain.bo.TaskReturnBo;
import org.dromara.agentoa.workflow.domain.bo.TaskTransferBo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.CcVo;
import org.dromara.agentoa.workflow.domain.vo.TaskVo;
import org.dromara.agentoa.workflow.service.IWorkflowTaskService;
import org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf/tasks")
public class WfTaskController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IWorkflowTaskService taskService;
    private final FlowIdempotencyGuard idempotencyGuard;

    @GetMapping("/todo")
    public R<PageVo<TaskVo>> todo(PageQuery page) {
        return R.ok(taskService.selectTodo(page));
    }

    @GetMapping("/done")
    public R<PageVo<TaskVo>> done(PageQuery page) {
        return R.ok(taskService.selectDone(page));
    }

    /** 抄送我的（P1，API 规范 4.4） */
    @GetMapping("/cc")
    public R<PageVo<CcVo>> cc(PageQuery page) {
        return R.ok(taskService.selectCc(page));
    }

    @GetMapping("/{taskId}")
    public R<TaskVo> get(@PathVariable String taskId) {
        return R.ok(taskService.selectTask(taskId));
    }

    /** 完成任务：action 只接受 agree/reject（API 规范 4.4） */
    @Log(title = "流程任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/complete")
    public R<TaskVo> complete(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                              @PathVariable String taskId,
                              @Validated @RequestBody TaskCompleteBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/tasks/" + taskId + "/complete", bo,
            () -> taskService.complete(taskId, bo));
    }

    /** 转办：任务保持待处理，记录旧/新办理人 */
    @Log(title = "流程任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/transfer")
    public R<TaskVo> transfer(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                              @PathVariable String taskId,
                              @Validated @RequestBody TaskTransferBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/tasks/" + taskId + "/transfer", bo,
            () -> taskService.transfer(taskId, bo));
    }

    /** 退回（P1）：退回到发起节点或历史节点 */
    @Log(title = "流程任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/return")
    public R<TaskVo> returnTask(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                @PathVariable String taskId,
                                @Validated @RequestBody TaskReturnBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/tasks/" + taskId + "/return", bo,
            () -> taskService.returnTask(taskId, bo));
    }

    /** 加签（P1）：before 前加签为共同办理人；after 后加签按顺序续办 */
    @Log(title = "流程任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/addsign")
    public R<TaskVo> addsign(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                             @PathVariable String taskId,
                             @Validated @RequestBody TaskAddsignBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/tasks/" + taskId + "/addsign", bo,
            () -> taskService.addsign(taskId, bo));
    }

    /** 催办（P1）：仅发起人，站内信提醒当前节点，不改变任务状态 */
    @Log(title = "流程任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/urge")
    public R<Void> urge(@PathVariable String taskId,
                        @RequestBody(required = false) UrgeBody body) {
        taskService.urge(taskId, body == null ? null : body.getComment());
        return R.ok();
    }

    public static class UrgeBody {
        private String comment;

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    private R<TaskVo> runIdempotent(String idempotencyKey, String path, Object body,
                                    java.util.function.Supplier<TaskVo> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        Long userId = LoginHelper.getUserId();
        String key = idempotencyKey.trim();
        String replayRef = idempotencyGuard.begin(userId, key, path,
            FlowIdempotencyGuard.digest(JsonUtils.toJsonString(body)));
        if (replayRef != null) {
            return R.ok(taskService.selectTaskResult(replayRef));
        }
        TaskVo vo = action.get();
        idempotencyGuard.complete(userId, key, String.valueOf(vo.getTaskId()));
        return R.ok(vo);
    }
}
