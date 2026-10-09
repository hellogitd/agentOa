package org.dromara.agentoa.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaJobPositionBo;
import org.dromara.agentoa.hr.domain.vo.OaJobPositionVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.IHrPositionService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 业务岗位接口（API 规范 3.2）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/posts")
public class HrPostController {

    private final IHrPositionService positionService;

    @SaCheckPermission("hr:post:list")
    @GetMapping
    public R<PageVo<OaJobPositionVo>> list(OaJobPositionBo query, HrPageQuery page) {
        return R.ok(positionService.selectPagePositions(query, page));
    }

    @SaCheckPermission("hr:post:query")
    @GetMapping("/{postId}")
    public R<OaJobPositionVo> get(@PathVariable Long postId) {
        return R.ok(positionService.selectPositionById(postId));
    }

    @SaCheckPermission("hr:post:add")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<OaJobPositionVo> add(@Validated @RequestBody OaJobPositionBo bo) {
        return R.ok(positionService.insertPosition(bo));
    }

    @SaCheckPermission("hr:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{postId}")
    public R<OaJobPositionVo> edit(@PathVariable Long postId, @Validated @RequestBody OaJobPositionBo bo) {
        return R.ok(positionService.updatePosition(postId, bo));
    }

    @SaCheckPermission("hr:post:remove")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{postId}")
    public R<Void> remove(@PathVariable Long postId) {
        positionService.deletePosition(postId);
        return R.ok();
    }
}
