package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiKbBo;
import org.dromara.agentoa.ai.domain.bo.AiKbMemberBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSourceBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiKbChunkVo;
import org.dromara.agentoa.ai.domain.vo.AiKbMemberVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSourceVo;
import org.dromara.agentoa.ai.domain.vo.AiKbVo;
import org.dromara.agentoa.ai.service.IAiKbService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理（docs/21 M3）：知识域/成员/数据源/索引/检索测试/分块预览。
 * <p>
 * 对象权限模式同知识库：不可见即 404；写操作走 @Log 审计。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/kb")
public class AiKbController {

    private final IAiKbService kbService;

    // ---------------------------------------------------------------- knowledge domains

    @SaCheckPermission(AiAccessPolicy.PERM_KB_QUERY)
    @GetMapping
    public R<PageVo<AiKbVo>> list(AiKbBo query, AiPageQuery page) {
        return R.ok(kbService.list(query, page, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_QUERY)
    @GetMapping("/{id}")
    public R<AiKbVo> get(@PathVariable Long id) {
        return R.ok(kbService.get(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_ADD)
    @Log(title = "AI 知识域", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<AiKbVo> create(@Validated @RequestBody AiKbBo bo) {
        return R.ok(kbService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_EDIT)
    @Log(title = "AI 知识域", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiKbVo> update(@PathVariable Long id, @Validated @RequestBody AiKbBo bo) {
        return R.ok(kbService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_REMOVE)
    @Log(title = "AI 知识域", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        kbService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    // ---------------------------------------------------------------- members

    @SaCheckPermission(AiAccessPolicy.PERM_KB_QUERY)
    @GetMapping("/{id}/members")
    public R<List<AiKbMemberVo>> members(@PathVariable Long id) {
        return R.ok(kbService.members(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_EDIT)
    @Log(title = "AI 知识域成员", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/{id}/members")
    public R<Void> addMembers(@PathVariable Long id, @Validated @RequestBody AiKbMemberBo bo) {
        kbService.addMembers(id, bo.getUserIds(), LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_EDIT)
    @Log(title = "AI 知识域成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        kbService.removeMember(id, userId, LoginHelper.getUserId());
        return R.ok();
    }

    // ---------------------------------------------------------------- sources

    @SaCheckPermission(AiAccessPolicy.PERM_KB_QUERY)
    @GetMapping("/{id}/sources")
    public R<PageVo<AiKbSourceVo>> sources(@PathVariable Long id, AiPageQuery page) {
        return R.ok(kbService.sources(id, page, LoginHelper.getUserId()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_ADD)
    @Log(title = "AI 知识域数据源", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping(value = "/{id}/sources", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<AiKbSourceVo> addDocumentSource(@PathVariable Long id, @Validated @RequestBody AiKbSourceBo bo) {
        return R.ok(kbService.addDocumentSource(id, bo, LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_ADD)
    @Log(title = "AI 知识域数据源", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping(value = "/{id}/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AiKbSourceVo> addFileSource(@PathVariable Long id, @RequestPart("file") MultipartFile file)
        throws Exception {
        return R.ok(kbService.addFileSource(id, file.getOriginalFilename(), file.getBytes(),
            LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_REMOVE)
    @Log(title = "AI 知识域数据源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}/sources/{sourceId}")
    public R<Void> removeSource(@PathVariable Long id, @PathVariable Long sourceId) {
        kbService.removeSource(id, sourceId, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_INDEX)
    @Log(title = "AI 知识域索引", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{id}/sources/{sourceId}/reindex")
    public R<AiKbSourceVo> reindex(@PathVariable Long id, @PathVariable Long sourceId) {
        return R.ok(kbService.reindex(id, sourceId, LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    // ---------------------------------------------------------------- search test and chunks

    @SaCheckPermission(AiAccessPolicy.PERM_KB_INDEX)
    @PostMapping("/{id}/search-test")
    public R<List<AiKbSearchHitVo>> searchTest(@PathVariable Long id, @Validated @RequestBody AiKbSearchTestBo bo) {
        return R.ok(kbService.searchTest(id, bo, LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_KB_QUERY)
    @GetMapping("/{id}/chunks")
    public R<PageVo<AiKbChunkVo>> chunks(@PathVariable Long id,
                                         @RequestParam(required = false) Long sourceId,
                                         AiPageQuery page) {
        return R.ok(kbService.chunks(id, sourceId, page, LoginHelper.getUserId()));
    }
}
