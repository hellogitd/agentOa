package org.dromara.agentoa.workflow.controller;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.GenericRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.bo.RequestSubmitBo;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.service.IGenericRequestService;
import org.dromara.agentoa.workflow.service.IWorkflowInstanceService;
import org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通用 OA 申请（M5）：纯 OA 表单不绑定具体业务表，直接按流程定义上的结构化审批链发起。
 * <p>
 * {@code POST /generic-requests/launch} 是「直接发起」入口：建单 + 发起流程一并完成。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf/generic-requests")
public class GenericRequestController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IGenericRequestService requestService;
    private final IWorkflowInstanceService instanceService;
    private final FlowIdempotencyGuard idempotencyGuard;

    @Log(title = "通用申请", businessType = BusinessType.INSERT)
    @PostMapping
    public R<BusinessRequestVo> add(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                    @Validated @RequestBody GenericRequestBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/generic-requests", bo,
            vo -> String.valueOf(vo.getId()),
            () -> requestService.createDraft(bo),
            ref -> requestService.selectRequest(Long.valueOf(ref)));
    }

    /** 纯 OA 表单直接发起：建单 + 发起流程一并完成 */
    @Log(title = "通用申请", businessType = BusinessType.INSERT)
    @PostMapping("/launch")
    public R<BusinessRequestVo> launch(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                       @Validated @RequestBody GenericRequestBo bo) {
        return runIdempotent(idempotencyKey, "/api/v1/wf/generic-requests/launch", bo,
            vo -> String.valueOf(vo.getId()),
            () -> requestService.launch(bo),
            ref -> requestService.selectRequest(Long.valueOf(ref)));
    }

    @Log(title = "通用申请", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{requestId}")
    public R<BusinessRequestVo> edit(@PathVariable Long requestId, @Validated @RequestBody GenericRequestBo bo) {
        return R.ok(requestService.updateDraft(requestId, bo));
    }

    @GetMapping("/{requestId}")
    public R<BusinessRequestVo> get(@PathVariable Long requestId) {
        return R.ok(requestService.selectRequest(requestId));
    }

    @GetMapping
    public R<PageVo<BusinessRequestVo>> list(PageQuery page) {
        return R.ok(requestService.selectPage(page));
    }

    @Log(title = "通用申请", businessType = BusinessType.UPDATE)
    @PostMapping("/{requestId}/submit")
    public R<InstanceStartVo> submit(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                     @PathVariable Long requestId,
                                     @RequestBody(required = false) RequestSubmitBo body) {
        Integer lockVersion = body == null ? null : body.getLockVersion();
        Map<String, Object> assigneeSelections = body == null ? null : body.getAssigneeSelections();
        return runIdempotent(idempotencyKey, "/api/v1/wf/generic-requests/" + requestId + "/submit", body,
            vo -> String.valueOf(vo.getInstanceId()),
            () -> requestService.submit(requestId, lockVersion, assigneeSelections),
            ref -> instanceService.selectStartResult(Long.valueOf(ref)));
    }

    private <T> R<T> runIdempotent(String idempotencyKey, String path, Object body,
                                   Function<T, String> refOf, Supplier<T> action, Function<String, T> replay) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        Long userId = LoginHelper.getUserId();
        String key = idempotencyKey.trim();
        String replayRef = idempotencyGuard.begin(userId, key, path,
            FlowIdempotencyGuard.digest(org.dromara.common.json.utils.JsonUtils.toJsonString(body)));
        if (replayRef != null) {
            return R.ok(replay.apply(replayRef));
        }
        T result = action.get();
        idempotencyGuard.complete(userId, key, refOf.apply(result));
        return R.ok(result);
    }
}
