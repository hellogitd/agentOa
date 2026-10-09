package org.dromara.agentoa.workflow.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.InstanceCcBo;
import org.dromara.agentoa.workflow.domain.bo.InstanceStartBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.InstanceDetailVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.domain.vo.TaskActionVo;
import org.dromara.agentoa.workflow.service.IWorkflowInstanceService;
import org.dromara.agentoa.workflow.service.IWorkflowTaskService;
import org.dromara.agentoa.workflow.service.support.FlowAttachmentAccess;
import org.dromara.agentoa.workflow.service.support.FlowAttachmentDownloader;
import org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf/instances")
public class WfInstanceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IWorkflowInstanceService instanceService;
    private final IWorkflowTaskService taskService;
    private final FlowIdempotencyGuard idempotencyGuard;
    private final FlowAttachmentDownloader attachmentDownloader;

    /** 发起流程（API 规范 4.3）：业务数据由承接单持有 */
    @Log(title = "流程实例", businessType = BusinessType.INSERT)
    @PostMapping("/start")
    public R<InstanceStartVo> start(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                    @Validated @RequestBody InstanceStartBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/instances/start", bo,
            vo -> String.valueOf(vo.getInstanceId()), () -> instanceService.start(bo));
    }

    /** scope=mine（默认）/ all（需 wf:instance:list） */
    @GetMapping
    public R<PageVo<InstanceVo>> list(@RequestParam(required = false, defaultValue = "mine") String scope,
                                      @RequestParam(required = false) String businessType,
                                      PageQuery page) {
        return R.ok(instanceService.selectPage(scope, businessType, page));
    }

    @GetMapping("/{instanceId}")
    public R<InstanceDetailVo> detail(@PathVariable Long instanceId) {
        return R.ok(instanceService.selectDetail(instanceId));
    }

    @GetMapping("/{instanceId}/history")
    public R<List<TaskActionVo>> history(@PathVariable Long instanceId) {
        return R.ok(instanceService.selectHistory(instanceId));
    }

    @GetMapping("/{instanceId}/diagram")
    public void diagram(@PathVariable Long instanceId, HttpServletResponse response) throws IOException {
        response.setContentType("image/png");
        response.getOutputStream().write(instanceService.diagram(instanceId));
    }

    /**
     * 表单附件下载（docs/23 H5-H3-01）：实例可见人且表单数据引用该文件才可下载，
     * 越权与未引用均按对象权限口径 404（不暴露文件存在性）。
     */
    @GetMapping("/{instanceId}/attachments/{fileId}/download")
    public ResponseEntity<byte[]> attachment(@PathVariable Long instanceId, @PathVariable Long fileId) {
        InstanceDetailVo detail = instanceService.selectDetail(instanceId);
        if (!FlowAttachmentAccess.referencedInForm(fileId, detail.getFormData())) {
            throw new ServiceException("WF_ATTACHMENT_NOT_FOUND 表单附件不存在或不可见", 404);
        }
        byte[] bytes = attachmentDownloader.load(fileId);
        if (bytes == null) {
            throw new ServiceException("WF_ATTACHMENT_NOT_FOUND 表单附件不存在或不可见", 404);
        }
        String name = attachmentDownloader.originalName(fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(bytes);
    }

    /** 撤销（API 规范 4.3 revoke）：仅发起人且无人处理过 */
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PutMapping("/{instanceId}/revoke")
    public R<InstanceVo> revoke(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                @PathVariable Long instanceId,
                                @RequestBody(required = false) RevokeBody body) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/instances/" + instanceId + "/revoke", body,
            vo -> String.valueOf(vo.getId()), () -> instanceService.revoke(instanceId));
    }

    /** 挂起（P1，API 规范 4.4） */
    @SaCheckPermission("wf:instance:suspend")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PutMapping("/{instanceId}/suspend")
    public R<InstanceVo> suspend(@PathVariable Long instanceId) {
        return R.ok(instanceService.suspend(instanceId));
    }

    /** 恢复（P1） */
    @SaCheckPermission("wf:instance:resume")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PutMapping("/{instanceId}/resume")
    public R<InstanceVo> resume(@PathVariable Long instanceId) {
        return R.ok(instanceService.resume(instanceId));
    }

    /** 强制终止（P1，WF-14） */
    @SaCheckPermission("wf:instance:terminate")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PutMapping("/{instanceId}/terminate")
    public R<InstanceVo> terminate(@PathVariable Long instanceId,
                                   @RequestBody(required = false) RevokeBody body) {
        return R.ok(instanceService.terminate(instanceId, body == null ? null : body.getReason()));
    }

    /** 超时扫描（P1，WF-10）：可由调度触发 */
    @SaCheckPermission("wf:instance:list")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PostMapping("/timeout-scan")
    public R<Integer> timeoutScan(@RequestParam(defaultValue = "24") int hours) {
        return R.ok(instanceService.scanTimeouts(hours));
    }

    /** 抄送（P1） */
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    @PostMapping("/{instanceId}/cc")
    public R<Void> cc(@PathVariable Long instanceId, @Validated @RequestBody InstanceCcBo bo) {
        taskService.cc(bo, instanceId);
        return R.ok();
    }

    public static class RevokeBody {
        private Integer lockVersion;
        private String reason;

        public Integer getLockVersion() {
            return lockVersion;
        }

        public void setLockVersion(Integer lockVersion) {
            this.lockVersion = lockVersion;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    private <T> R<T> runIdempotent(String idempotencyKey, String path, Object body,
                                   java.util.function.Function<T, String> refOf,
                                   java.util.function.Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        Long userId = LoginHelper.getUserId();
        String key = idempotencyKey.trim();
        String replayRef = idempotencyGuard.begin(userId, key, path,
            FlowIdempotencyGuard.digest(JsonUtils.toJsonString(body)));
        if (replayRef != null) {
            return R.ok(replay(replayRef));
        }
        T result = action.get();
        idempotencyGuard.complete(userId, key, refOf.apply(result));
        return R.ok(result);
    }

    @SuppressWarnings("unchecked")
    private <T> T replay(String replayRef) {
        return (T) instanceService.selectStartResult(Long.valueOf(replayRef));
    }
}
