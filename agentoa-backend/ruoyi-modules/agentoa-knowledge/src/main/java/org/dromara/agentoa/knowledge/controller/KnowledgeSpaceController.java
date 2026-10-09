package org.dromara.agentoa.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.bo.SpacePageQuery;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.MemberVo;
import org.dromara.agentoa.knowledge.domain.vo.SpaceVo;
import org.dromara.agentoa.knowledge.service.IKnowledgeSpaceService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识空间与成员接口（docs/16 API 与规则）：读接口登录即可，写操作需按钮权限 + 空间角色。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/knowledge/spaces")
public class KnowledgeSpaceController {

    private final IKnowledgeSpaceService spaceService;

    @GetMapping
    public R<PageVo<SpaceVo>> list(SpacePageQuery query) {
        return R.ok(spaceService.list(query, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<SpaceVo> detail(@PathVariable Long id) {
        return R.ok(spaceService.detail(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_SPACE_ADD)
    @Log(title = "知识空间", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<SpaceVo> add(@Validated @RequestBody SpaceBo bo) {
        return R.ok(spaceService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_SPACE_EDIT)
    @Log(title = "知识空间", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<SpaceVo> edit(@PathVariable Long id, @Validated @RequestBody SpaceBo bo) {
        return R.ok(spaceService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_SPACE_REMOVE)
    @Log(title = "知识空间", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        spaceService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    @GetMapping("/{id}/members")
    public R<List<MemberVo>> members(@PathVariable Long id) {
        return R.ok(spaceService.members(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_MEMBER_ADD)
    @Log(title = "知识空间成员", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{id}/members")
    public R<MemberVo> addMember(@PathVariable Long id, @Validated @RequestBody MemberBo bo) {
        return R.ok(spaceService.addMember(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_MEMBER_EDIT)
    @Log(title = "知识空间成员", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}/members/{userId}")
    public R<MemberVo> editMember(@PathVariable Long id, @PathVariable Long userId,
                                  @Validated @RequestBody MemberBo bo) {
        return R.ok(spaceService.updateMember(id, userId, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_MEMBER_REMOVE)
    @Log(title = "知识空间成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        spaceService.removeMember(id, userId, LoginHelper.getUserId());
        return R.ok();
    }
}
