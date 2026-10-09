package org.dromara.agentoa.knowledge.service;

import org.dromara.agentoa.knowledge.domain.bo.CommentBo;
import org.dromara.agentoa.knowledge.domain.vo.CommentVo;
import org.dromara.agentoa.knowledge.domain.vo.SocialStatVo;

import java.util.List;

/**
 * 文档社交服务（P1，KB-06/KB-07）：评论、点赞、收藏。
 * <p>
 * 全部复用空间/文档同一鉴权：查看需 VIEWER 及以上，评论需 COMMENTER 及以上；
 * 点赞/收藏幂等（重复不重复计数）。
 */
public interface IDocumentSocialService {

    List<CommentVo> selectComments(Long documentId);

    CommentVo addComment(Long documentId, CommentBo bo);

    void deleteComment(Long commentId);

    /** 点赞（幂等），返回最新点赞数 */
    long like(Long documentId);

    /** 取消点赞（幂等），返回最新点赞数 */
    long unlike(Long documentId);

    /** 收藏（幂等） */
    void favorite(Long documentId);

    /** 取消收藏（幂等） */
    void unfavorite(Long documentId);

    /** 文档社交统计 */
    SocialStatVo stat(Long documentId);

    /** 我的收藏文档 ID 列表（按收藏时间倒序） */
    List<Long> myFavorites();
}
