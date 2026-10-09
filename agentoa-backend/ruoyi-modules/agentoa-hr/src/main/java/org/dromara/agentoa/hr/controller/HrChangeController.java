package org.dromara.agentoa.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaChangeBo;
import org.dromara.agentoa.hr.domain.vo.OaChangeVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.IHrChangeService;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工异动接口（API 规范 3.6 调岗调薪）。
 * <p>
 * 发起异动为命令类写接口，必须携带 Idempotency-Key（规范 1.7）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/changes")
public class HrChangeController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IHrChangeService changeService;
    private final IdempotencyGuard idempotencyGuard;

    @SaCheckPermission("hr:change:list")
    @GetMapping
    public R<PageVo<OaChangeVo>> list(OaChangeBo query, HrPageQuery page) {
        return R.ok(changeService.selectPageChanges(query, page));
    }

    @SaCheckPermission("hr:change:query")
    @GetMapping("/{changeId}")
    public R<OaChangeVo> get(@PathVariable Long changeId) {
        return R.ok(changeService.selectChange(changeId));
    }

    /** 发起调岗/调薪/晋升/降级异动 */
    @SaCheckPermission("hr:change:add")
    @Log(title = "员工异动", businessType = BusinessType.INSERT)
    @PostMapping
    public R<OaChangeVo> add(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                             @Validated @RequestBody OaChangeBo bo) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        Long userId = LoginHelper.getUserId();
        String key = idempotencyKey.trim();
        String replayRef = idempotencyGuard.begin(userId, key, "/api/v1/hr/changes",
            IdempotencyGuard.digest(JsonUtils.toJsonString(bo)));
        if (replayRef != null) {
            return R.ok(changeService.selectChange(Long.valueOf(replayRef)));
        }
        OaChangeVo vo = changeService.createChange(bo);
        idempotencyGuard.complete(userId, key, String.valueOf(vo.getId()));
        return R.ok(vo);
    }

    /** 执行到期未生效的异动（幂等，可由调度或 HR 手工触发） */
    @SaCheckPermission("hr:change:add")
    @Log(title = "员工异动", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/apply-due")
    public R<Integer> applyDue() {
        return R.ok(changeService.applyDueChanges());
    }
}
