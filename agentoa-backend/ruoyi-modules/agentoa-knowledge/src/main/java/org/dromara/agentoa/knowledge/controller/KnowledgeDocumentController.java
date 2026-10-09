package org.dromara.agentoa.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentDraftBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentPageQuery;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.DocumentDraftVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVersionVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVo;
import org.dromara.agentoa.knowledge.domain.vo.SearchHitVo;
import org.dromara.agentoa.knowledge.service.IKnowledgeDocumentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识文档接口（docs/16：baseVersion 乐观锁、版本历史、回收站与搜索）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeDocumentController {

    private final IKnowledgeDocumentService documentService;

    @GetMapping
    public R<PageVo<DocumentVo>> list(DocumentPageQuery query) {
        return R.ok(documentService.list(query, LoginHelper.getUserId()));
    }

    @GetMapping("/search")
    public R<PageVo<SearchHitVo>> search(DocumentPageQuery query) {
        return R.ok(documentService.search(query, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<DocumentVo> detail(@PathVariable Long id) {
        return R.ok(documentService.detail(id, LoginHelper.getUserId()));
    }

    /** 批量详情（收藏列表等场景）：一次取多篇，服务端按授权过滤 */
    @GetMapping("/batch")
    public R<List<DocumentVo>> details(@RequestParam("ids") List<Long> ids) {
        return R.ok(documentService.details(ids, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_ADD)
    @Log(title = "知识文档", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<DocumentVo> add(@Validated @RequestBody DocumentBo bo) {
        return R.ok(documentService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_EDIT)
    @Log(title = "知识文档", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<DocumentVo> edit(@PathVariable Long id, @Validated @RequestBody DocumentBo bo) {
        return R.ok(documentService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_REMOVE)
    @Log(title = "知识文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        documentService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_RESTORE)
    @Log(title = "知识文档恢复", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/restore")
    public R<DocumentVo> restore(@PathVariable Long id) {
        return R.ok(documentService.restore(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_EDIT)
    @Log(title = "知识文档发布", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/publish")
    public R<DocumentVo> publish(@PathVariable Long id) {
        return R.ok(documentService.publish(id, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_EDIT)
    @Log(title = "知识文档归档", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/archive")
    public R<DocumentVo> archive(@PathVariable Long id) {
        return R.ok(documentService.archive(id, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}/versions")
    public R<List<DocumentVersionVo>> versions(@PathVariable Long id) {
        return R.ok(documentService.versions(id, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}/versions/{version}")
    public R<DocumentVersionVo> version(@PathVariable Long id, @PathVariable Integer version) {
        return R.ok(documentService.version(id, version, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_EDIT)
    @Log(title = "知识文档回滚", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}/rollback/{version}")
    public R<DocumentVo> rollback(@PathVariable Long id, @PathVariable Integer version) {
        return R.ok(documentService.rollback(id, version, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_EDIT)
    @PutMapping("/{id}/draft")
    public R<Void> saveDraft(@PathVariable Long id, @Validated @RequestBody DocumentDraftBo bo) {
        documentService.saveDraft(id, bo, LoginHelper.getUserId());
        return R.ok();
    }

    @GetMapping("/{id}/draft")
    public R<DocumentDraftVo> draft(@PathVariable Long id) {
        return R.ok(documentService.draft(id, LoginHelper.getUserId()));
    }
}
