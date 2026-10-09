package org.dromara.agentoa.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentComment;
import org.dromara.agentoa.knowledge.domain.OaDocumentFavorite;
import org.dromara.agentoa.knowledge.domain.OaDocumentLike;
import org.dromara.agentoa.knowledge.domain.bo.CommentBo;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.vo.CommentVo;
import org.dromara.agentoa.knowledge.domain.vo.SocialStatVo;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentCommentMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentFavoriteMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentLikeMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.service.IDocumentSocialService;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 文档社交服务实现（P1）。约束：
 * 评论需 COMMENTER 及以上，删除仅作者或空间管理员；点赞/收藏唯一键幂等。
 */
@RequiredArgsConstructor
@Service
public class DocumentSocialServiceImpl implements IDocumentSocialService {

    private final OaDocumentCommentMapper commentMapper;
    private final OaDocumentLikeMapper likeMapper;
    private final OaDocumentFavoriteMapper favoriteMapper;
    private final OaDocumentMapper documentMapper;
    private final KnowledgeAuthorization authorization;
    private final KnowledgeIdentityMapper identityMapper;

    @Override
    public List<CommentVo> selectComments(Long documentId) {
        Long userId = LoginHelper.getUserId();
        OaDocument document = requireDocument(documentId);
        authorization.requireView(userId, document);
        return commentMapper.selectList(new LambdaQueryWrapper<OaDocumentComment>()
                .eq(OaDocumentComment::getDocumentId, documentId)
                .orderByAsc(OaDocumentComment::getCreateTime)
                .orderByAsc(OaDocumentComment::getId))
            .stream().map(this::toCommentVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVo addComment(Long documentId, CommentBo bo) {
        Long userId = LoginHelper.getUserId();
        OaDocument document = requireDocument(documentId);
        requireCommenter(userId, document);
        if (bo.getParentId() != null) {
            OaDocumentComment parent = commentMapper.selectById(bo.getParentId());
            if (parent == null || !documentId.equals(parent.getDocumentId())) {
                throw new ServiceException("KN_NOT_FOUND 父评论不存在", 404);
            }
        }
        OaDocumentComment comment = new OaDocumentComment();
        comment.setDocumentId(documentId);
        comment.setParentId(bo.getParentId() == null ? 0L : bo.getParentId());
        comment.setContent(bo.getContent().trim());
        commentMapper.insert(comment);
        return toCommentVo(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Long userId = LoginHelper.getUserId();
        OaDocumentComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new ServiceException("KN_NOT_FOUND 评论不存在", 404);
        }
        OaDocument document = requireDocument(comment.getDocumentId());
        SpaceRole role = authorization.documentRole(userId, document);
        boolean author = userId.equals(comment.getCreateBy());
        boolean manager = role != null && role.rank() >= SpaceRole.EDITOR.rank();
        if (!author && !manager) {
            throw new ServiceException("KN_FORBIDDEN 无权删除该评论", 403);
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long like(Long documentId) {
        Long userId = LoginHelper.getUserId();
        OaDocument document = requireDocument(documentId);
        authorization.requireView(userId, document);
        OaDocumentLike like = new OaDocumentLike();
        like.setDocumentId(documentId);
        like.setUserId(userId);
        like.setCreateTime(new Date());
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException ignored) {
            // 重复点赞不重复计数
        }
        return likeCount(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long unlike(Long documentId) {
        Long userId = LoginHelper.getUserId();
        likeMapper.delete(new LambdaQueryWrapper<OaDocumentLike>()
            .eq(OaDocumentLike::getDocumentId, documentId)
            .eq(OaDocumentLike::getUserId, userId));
        return likeCount(documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long documentId) {
        Long userId = LoginHelper.getUserId();
        OaDocument document = requireDocument(documentId);
        authorization.requireView(userId, document);
        OaDocumentFavorite favorite = new OaDocumentFavorite();
        favorite.setDocumentId(documentId);
        favorite.setUserId(userId);
        favorite.setCreateTime(new Date());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            // 重复收藏幂等
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long documentId) {
        Long userId = LoginHelper.getUserId();
        favoriteMapper.delete(new LambdaQueryWrapper<OaDocumentFavorite>()
            .eq(OaDocumentFavorite::getDocumentId, documentId)
            .eq(OaDocumentFavorite::getUserId, userId));
    }

    @Override
    public SocialStatVo stat(Long documentId) {
        Long userId = LoginHelper.getUserId();
        OaDocument document = requireDocument(documentId);
        authorization.requireView(userId, document);
        SocialStatVo vo = new SocialStatVo();
        vo.setDocumentId(documentId);
        vo.setLikeCount(likeCount(documentId));
        vo.setLikedByMe(likeMapper.exists(new LambdaQueryWrapper<OaDocumentLike>()
            .eq(OaDocumentLike::getDocumentId, documentId)
            .eq(OaDocumentLike::getUserId, userId)));
        vo.setFavoritedByMe(favoriteMapper.exists(new LambdaQueryWrapper<OaDocumentFavorite>()
            .eq(OaDocumentFavorite::getDocumentId, documentId)
            .eq(OaDocumentFavorite::getUserId, userId)));
        return vo;
    }

    @Override
    public List<Long> myFavorites() {
        Long userId = LoginHelper.getUserId();
        return favoriteMapper.selectList(new LambdaQueryWrapper<OaDocumentFavorite>()
                .eq(OaDocumentFavorite::getUserId, userId)
                .orderByDesc(OaDocumentFavorite::getCreateTime))
            .stream().map(OaDocumentFavorite::getDocumentId).toList();
    }

    // ---------------------------------------------------------------- 内部方法

    private void requireCommenter(Long userId, OaDocument document) {
        SpaceRole role = authorization.documentRole(userId, document);
        if (role == null || role.rank() < SpaceRole.COMMENTER.rank()) {
            throw new ServiceException("KN_FORBIDDEN 无评论权限", 403);
        }
    }

    private long likeCount(Long documentId) {
        return likeMapper.selectCount(new LambdaQueryWrapper<OaDocumentLike>()
            .eq(OaDocumentLike::getDocumentId, documentId));
    }

    private OaDocument requireDocument(Long documentId) {
        OaDocument document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null) {
            throw new ServiceException("KN_NOT_FOUND 文档不存在", 404);
        }
        return document;
    }

    private CommentVo toCommentVo(OaDocumentComment comment) {
        CommentVo vo = new CommentVo();
        vo.setId(comment.getId());
        vo.setDocumentId(comment.getDocumentId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setCreateBy(comment.getCreateBy());
        vo.setCreateByName(comment.getCreateBy() == null ? null : identityMapper.selectNickName(comment.getCreateBy()));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
