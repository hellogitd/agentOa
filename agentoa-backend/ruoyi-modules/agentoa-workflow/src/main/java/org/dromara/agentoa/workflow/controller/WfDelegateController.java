package org.dromara.agentoa.workflow.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.DelegateBo;
import org.dromara.agentoa.workflow.domain.vo.DelegateVo;
import org.dromara.agentoa.workflow.service.IWorkflowDelegateService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委托代理接口（P1，需求 WF-12，API 规范 4.5）。
 * <p>
 * 对象级授权：用户维护自己的委托，管理员可代查。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf/delegates")
public class WfDelegateController {

    private final IWorkflowDelegateService delegateService;

    @GetMapping
    public R<List<DelegateVo>> list(@RequestParam(required = false) Long ownerId) {
        return R.ok(delegateService.selectDelegates(ownerId));
    }

    @GetMapping("/{delegateId}")
    public R<DelegateVo> get(@PathVariable Long delegateId) {
        return R.ok(delegateService.selectDelegate(delegateId));
    }

    @Log(title = "委托代理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<DelegateVo> add(@Validated @RequestBody DelegateBo bo) {
        return R.ok(delegateService.createDelegate(bo));
    }

    @Log(title = "委托代理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{delegateId}")
    public R<DelegateVo> edit(@PathVariable Long delegateId, @Validated @RequestBody DelegateBo bo) {
        return R.ok(delegateService.updateDelegate(delegateId, bo));
    }

    @Log(title = "委托代理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{delegateId}")
    public R<Void> remove(@PathVariable Long delegateId) {
        delegateService.deleteDelegate(delegateId);
        return R.ok();
    }
}
