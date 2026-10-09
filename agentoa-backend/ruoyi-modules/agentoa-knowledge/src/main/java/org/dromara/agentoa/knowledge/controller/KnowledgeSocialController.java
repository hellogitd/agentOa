package org.dromara.agentoa.knowledge.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.knowledge.domain.bo.CommentBo;
import org.dromara.agentoa.knowledge.domain.vo.CommentVo;
import org.dromara.agentoa.knowledge.domain.vo.SocialStatVo;
import org.dromara.agentoa.knowledge.service.IDocumentSocialService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档社交接口（P1，KB-06/KB-07）：评论、点赞、收藏。
 * <p>
 * 对象级授权在服务层判定（与详情/下载同一鉴权口径）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/knowledge/documents/{documentId}")
public class KnowledgeSocialController {

    private final IDocumentSocialService socialService;

    // ---------------------------------------------------------------- 评论

    @GetMapping("/comments")
    public R<List<CommentVo>> listComments(@PathVariable Long documentId) {
        return R.ok(socialService.selectComments(documentId));
    }

    @Log(title = "文档评论", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/comments")
    public R<CommentVo> addComment(@PathVariable Long documentId, @Validated @RequestBody CommentBo bo) {
        return R.ok(socialService.addComment(documentId, bo));
    }

    @Log(title = "文档评论", businessType = BusinessType.DELETE)
    @DeleteMapping("/comments/{commentId}")
    public R<Void> removeComment(@PathVariable Long documentId, @PathVariable Long commentId) {
        socialService.deleteComment(commentId);
        return R.ok();
    }

    // ---------------------------------------------------------------- 点赞

    /** 点赞（幂等），返回最新点赞数 */
    @PostMapping("/like")
    public R<Long> like(@PathVariable Long documentId) {
        return R.ok(socialService.like(documentId));
    }

    /** 取消点赞（幂等），返回最新点赞数 */
    @DeleteMapping("/like")
    public R<Long> unlike(@PathVariable Long documentId) {
        return R.ok(socialService.unlike(documentId));
    }

    // ---------------------------------------------------------------- 收藏

    @PostMapping("/favorite")
    public R<Void> favorite(@PathVariable Long documentId) {
        socialService.favorite(documentId);
        return R.ok();
    }

    @DeleteMapping("/favorite")
    public R<Void> unfavorite(@PathVariable Long documentId) {
        socialService.unfavorite(documentId);
        return R.ok();
    }

    /** 社交统计（点赞数/我是否点赞/我是否收藏） */
    @GetMapping("/social")
    public R<SocialStatVo> social(@PathVariable Long documentId) {
        return R.ok(socialService.stat(documentId));
    }
}
