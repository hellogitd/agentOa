package org.dromara.agentoa.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentRevision;
import org.dromara.agentoa.knowledge.domain.OaDocumentVersion;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeSpace;
import org.dromara.agentoa.knowledge.domain.bo.DocumentBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentDraftBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentPageQuery;
import org.dromara.agentoa.knowledge.domain.enums.DocumentStatus;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.DocumentDraftVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVersionVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVo;
import org.dromara.agentoa.knowledge.domain.vo.SearchHitVo;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentRevisionMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentVersionMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeSpaceMapper;
import org.dromara.agentoa.knowledge.service.IKnowledgeDocumentService;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.agentoa.knowledge.service.support.MarkdownText;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 文档实现：baseVersion 乐观锁（冲突 409），版本历史只追加不可覆盖，
 * 删除进回收站（30 天可恢复），列表/搜索都在服务端按授权过滤。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements IKnowledgeDocumentService {

    /** 回收站恢复期限（docs/02 KB-09） */
    static final long RESTORE_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final OaDocumentMapper documentMapper;
    private final OaDocumentVersionMapper versionMapper;
    private final OaDocumentRevisionMapper revisionMapper;
    private final OaKnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIdentityMapper identityMapper;
    private final KnowledgeAuthorization authorization;

    @Override
    public PageVo<DocumentVo> list(DocumentPageQuery query, Long actorUserId) {
        if (query.getSpaceId() != null) {
            authorization.requireSpaceView(actorUserId, query.getSpaceId());
        }
        Set<Long> spaceIds = authorization.authorizedSpaceIds(actorUserId);
        Set<Long> aclDocIds = authorization.aclOnlyDocumentIds(actorUserId);
        LambdaQueryWrapper<OaDocument> wrapper = new LambdaQueryWrapper<>();
        if (query.getSpaceId() != null) {
            wrapper.eq(OaDocument::getSpaceId, query.getSpaceId());
        } else {
            applyVisibilityFilter(wrapper, spaceIds, aclDocIds);
        }
        if (query.deletedOnly()) {
            wrapper.isNotNull(OaDocument::getDeletedAt);
        } else {
            wrapper.isNull(OaDocument::getDeletedAt);
        }
        if (query.getParentId() != null) {
            wrapper.eq(OaDocument::getParentId, query.getParentId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OaDocument::getStatus, query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(OaDocument::getTitle, like)
                .or().like(OaDocument::getTags, like)
                .or().like(OaDocument::getContentText, like));
        }
        wrapper.orderByDesc(OaDocument::getIsTop).orderByDesc(OaDocument::getUpdateTime);
        return pageDocuments(wrapper, query, actorUserId);
    }

    @Override
    public DocumentVo detail(Long id, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireView(actorUserId, document);
        return toVo(document, actorUserId, true);
    }

    @Override
    public List<DocumentVo> details(List<Long> ids, Long actorUserId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        distinct.removeIf(java.util.Objects::isNull);
        if (distinct.size() > 200) {
            distinct = new ArrayList<>(distinct.subList(0, 200));
        }
        List<DocumentVo> result = new ArrayList<>();
        for (OaDocument document : documentMapper.selectBatchIds(distinct)) {
            if (document == null || document.getDeletedAt() != null) {
                continue;
            }
            if (!KnowledgeAccessPolicy.canView(authorization.documentRole(actorUserId, document))) {
                continue;
            }
            result.add(toVo(document, actorUserId, true));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo create(DocumentBo bo, Long actorUserId) {
        authorization.requireSpaceView(actorUserId, bo.getSpaceId());
        OaKnowledgeSpace space = spaceMapper.selectById(bo.getSpaceId());
        SpaceRole role = authorization.spaceRole(actorUserId, space);
        if (!KnowledgeAccessPolicy.canEdit(role)) {
            throw new ServiceException("KN_FORBIDDEN 无编辑权限", 403);
        }
        long parentId = bo.getParentId() == null ? 0L : bo.getParentId();
        if (parentId != 0L) {
            OaDocument parent = require(parentId);
            if (!parent.getSpaceId().equals(bo.getSpaceId()) || parent.getDeletedAt() != null) {
                throw new ServiceException("KN_PARAM_INVALID 父文档不可用", 400);
            }
        }
        Date now = new Date();
        OaDocument document = new OaDocument();
        document.setSpaceId(bo.getSpaceId());
        document.setParentId(parentId);
        document.setTitle(bo.getTitle().trim());
        document.setContent(bo.getContent());
        document.setContentText(MarkdownText.strip(bo.getContent()));
        document.setDocType(bo.getDocType() == null ? "markdown" : bo.getDocType());
        document.setTags(bo.getTags());
        document.setVersion(1);
        document.setStatus(DocumentStatus.DRAFT.code());
        document.setIsTop(0);
        document.setViewCount(0);
        document.setLastEditBy(actorUserId);
        document.setLastEditTime(now);
        document.setCreateBy(actorUserId);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        documentMapper.insert(document);
        appendVersion(document, actorUserId, "创建文档");
        return toVo(document, actorUserId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo update(Long id, DocumentBo bo, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireView(actorUserId, document);
        if (document.getDeletedAt() != null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，请先恢复", 409);
        }
        authorization.requireEdit(actorUserId, document);
        if (document.getStatus() == DocumentStatus.ARCHIVED.code()) {
            throw new ServiceException("KN_STATE_CONFLICT 已归档文档不可编辑", 409);
        }
        if (bo.getBaseVersion() == null) {
            throw new ServiceException("KN_VERSION_MISSING 更新必须携带 baseVersion", 400);
        }
        if (!bo.getBaseVersion().equals(document.getVersion())) {
            throw new ServiceException("VERSION_CONFLICT 文档已被他人修改，请刷新后重试", 409);
        }
        Date now = new Date();
        int nextVersion = document.getVersion() + 1;
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<OaDocument>()
            .eq(OaDocument::getId, id)
            .eq(OaDocument::getVersion, bo.getBaseVersion())
            .set(OaDocument::getTitle, bo.getTitle().trim())
            .set(OaDocument::getContent, bo.getContent())
            .set(OaDocument::getContentText, MarkdownText.strip(bo.getContent()))
            .set(OaDocument::getTags, bo.getTags())
            .set(OaDocument::getVersion, nextVersion)
            .set(OaDocument::getLastEditBy, actorUserId)
            .set(OaDocument::getLastEditTime, now)
            .set(OaDocument::getUpdateTime, now));
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 文档已被他人修改，请刷新后重试", 409);
        }
        document.setTitle(bo.getTitle().trim());
        document.setContent(bo.getContent());
        document.setContentText(MarkdownText.strip(bo.getContent()));
        document.setTags(bo.getTags());
        document.setVersion(nextVersion);
        document.setLastEditBy(actorUserId);
        document.setLastEditTime(now);
        document.setUpdateTime(now);
        try {
            appendVersion(document, actorUserId, bo.getChangeSummary());
        } catch (DuplicateKeyException e) {
            throw new ServiceException("VERSION_CONFLICT 版本历史冲突，请刷新后重试", 409);
        }
        return toVo(document, actorUserId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaDocument document = require(id);
        if (document.getDeletedAt() != null) {
            return;
        }
        authorization.requireEdit(actorUserId, document);
        Date now = new Date();
        documentMapper.update(null, new LambdaUpdateWrapper<OaDocument>()
            .eq(OaDocument::getId, id)
            .isNull(OaDocument::getDeletedAt)
            .set(OaDocument::getDeletedAt, now)
            .set(OaDocument::getDeletedBy, actorUserId)
            .set(OaDocument::getUpdateTime, now));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo restore(Long id, Long actorUserId) {
        OaDocument document = require(id);
        if (document.getDeletedAt() == null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档不在回收站", 409);
        }
        if (System.currentTimeMillis() - document.getDeletedAt().getTime() > RESTORE_WINDOW_MILLIS) {
            throw new ServiceException("KN_STATE_CONFLICT 已超过 30 天恢复期限", 409);
        }
        authorization.requireEdit(actorUserId, document);
        Date now = new Date();
        documentMapper.update(null, new LambdaUpdateWrapper<OaDocument>()
            .eq(OaDocument::getId, id)
            .set(OaDocument::getDeletedAt, null)
            .set(OaDocument::getDeletedBy, null)
            .set(OaDocument::getUpdateTime, now));
        document.setDeletedAt(null);
        document.setDeletedBy(null);
        document.setUpdateTime(now);
        return toVo(document, actorUserId, false);
    }

    @Override
    public List<DocumentVersionVo> versions(Long id, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireView(actorUserId, document);
        List<DocumentVersionVo> list = new ArrayList<>();
        for (OaDocumentVersion version : versionMapper.selectList(new LambdaQueryWrapper<OaDocumentVersion>()
            .eq(OaDocumentVersion::getDocumentId, id)
            .orderByDesc(OaDocumentVersion::getVersion))) {
            list.add(toVo(version, false));
        }
        return list;
    }

    @Override
    public DocumentVersionVo version(Long id, Integer version, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireView(actorUserId, document);
        return toVo(requireVersion(id, version), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo rollback(Long id, Integer version, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireEdit(actorUserId, document);
        if (document.getDeletedAt() != null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，请先恢复", 409);
        }
        if (document.getStatus() == DocumentStatus.ARCHIVED.code()) {
            throw new ServiceException("KN_STATE_CONFLICT 已归档文档不可回滚", 409);
        }
        OaDocumentVersion snapshot = requireVersion(id, version);
        Date now = new Date();
        int nextVersion = document.getVersion() + 1;
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<OaDocument>()
            .eq(OaDocument::getId, id)
            .eq(OaDocument::getVersion, document.getVersion())
            .set(OaDocument::getTitle, snapshot.getTitle())
            .set(OaDocument::getContent, snapshot.getContent())
            .set(OaDocument::getContentText, MarkdownText.strip(snapshot.getContent()))
            .set(OaDocument::getVersion, nextVersion)
            .set(OaDocument::getLastEditBy, actorUserId)
            .set(OaDocument::getLastEditTime, now)
            .set(OaDocument::getUpdateTime, now));
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 文档已被他人修改，请刷新后重试", 409);
        }
        document.setTitle(snapshot.getTitle());
        document.setContent(snapshot.getContent());
        document.setContentText(MarkdownText.strip(snapshot.getContent()));
        document.setVersion(nextVersion);
        document.setUpdateTime(now);
        try {
            appendVersion(document, actorUserId, "回滚到版本 " + version);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("VERSION_CONFLICT 版本历史冲突，请刷新后重试", 409);
        }
        return toVo(document, actorUserId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long id, DocumentDraftBo bo, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireEdit(actorUserId, document);
        if (document.getDeletedAt() != null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，无法保存草稿", 409);
        }
        OaDocumentRevision revision = revisionMapper.selectOne(new LambdaQueryWrapper<OaDocumentRevision>()
            .eq(OaDocumentRevision::getDocumentId, id)
            .eq(OaDocumentRevision::getUserId, actorUserId));
        if (revision == null) {
            revision = new OaDocumentRevision();
            revision.setDocumentId(id);
            revision.setUserId(actorUserId);
        }
        revision.setBaseVersion(bo.getBaseVersion() == null ? document.getVersion() : bo.getBaseVersion());
        revision.setTitle(bo.getTitle());
        revision.setContent(bo.getContent());
        revision.setUpdateTime(new Date());
        if (revision.getId() == null) {
            try {
                revisionMapper.insert(revision);
            } catch (DuplicateKeyException e) {
                revision = revisionMapper.selectOne(new LambdaQueryWrapper<OaDocumentRevision>()
                    .eq(OaDocumentRevision::getDocumentId, id)
                    .eq(OaDocumentRevision::getUserId, actorUserId));
                revision.setBaseVersion(bo.getBaseVersion() == null ? revision.getBaseVersion() : bo.getBaseVersion());
                revision.setTitle(bo.getTitle());
                revision.setContent(bo.getContent());
                revision.setUpdateTime(new Date());
                revisionMapper.updateById(revision);
            }
        } else {
            revisionMapper.updateById(revision);
        }
    }

    @Override
    public DocumentDraftVo draft(Long id, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireView(actorUserId, document);
        OaDocumentRevision revision = revisionMapper.selectOne(new LambdaQueryWrapper<OaDocumentRevision>()
            .eq(OaDocumentRevision::getDocumentId, id)
            .eq(OaDocumentRevision::getUserId, actorUserId));
        if (revision == null) {
            return null;
        }
        DocumentDraftVo vo = new DocumentDraftVo();
        vo.setDocumentId(revision.getDocumentId());
        vo.setUserId(revision.getUserId());
        vo.setBaseVersion(revision.getBaseVersion());
        vo.setTitle(revision.getTitle());
        vo.setContent(revision.getContent());
        vo.setUpdateTime(String.valueOf(revision.getUpdateTime()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo publish(Long id, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireEdit(actorUserId, document);
        if (document.getDeletedAt() != null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，请先恢复", 409);
        }
        if (document.getStatus() != DocumentStatus.DRAFT.code()) {
            throw new ServiceException("KN_STATE_CONFLICT 仅草稿可发布", 409);
        }
        document.setStatus(DocumentStatus.PUBLISHED.code());
        document.setUpdateTime(new Date());
        documentMapper.updateById(document);
        return toVo(document, actorUserId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVo archive(Long id, Long actorUserId) {
        OaDocument document = require(id);
        authorization.requireEdit(actorUserId, document);
        if (document.getDeletedAt() != null) {
            throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，请先恢复", 409);
        }
        if (document.getStatus() == DocumentStatus.ARCHIVED.code()) {
            throw new ServiceException("KN_STATE_CONFLICT 文档已归档", 409);
        }
        document.setStatus(DocumentStatus.ARCHIVED.code());
        document.setUpdateTime(new Date());
        documentMapper.updateById(document);
        return toVo(document, actorUserId, false);
    }

    @Override
    public PageVo<SearchHitVo> search(DocumentPageQuery query, Long actorUserId) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (keyword.isEmpty()) {
            throw new ServiceException("KN_PARAM_INVALID 搜索关键字不能为空", 400);
        }
        Set<Long> spaceIds = authorization.authorizedSpaceIds(actorUserId);
        Set<Long> aclDocIds = authorization.aclOnlyDocumentIds(actorUserId);
        LambdaQueryWrapper<OaDocument> wrapper = new LambdaQueryWrapper<>();
        applyVisibilityFilter(wrapper, spaceIds, aclDocIds);
        wrapper.isNull(OaDocument::getDeletedAt);
        wrapper.and(w -> w.like(OaDocument::getTitle, keyword)
            .or().like(OaDocument::getContentText, keyword)
            .or().like(OaDocument::getTags, keyword));
        wrapper.orderByDesc(OaDocument::getUpdateTime);
        IPage<OaDocument> result = documentMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<SearchHitVo> records = new ArrayList<>();
        for (OaDocument document : result.getRecords()) {
            SearchHitVo hit = new SearchHitVo();
            hit.setDocumentId(document.getId());
            hit.setTitle(document.getTitle());
            hit.setHighlight(MarkdownText.highlight(document.getContentText(), keyword));
            OaKnowledgeSpace space = spaceMapper.selectById(document.getSpaceId());
            hit.setSpaceName(space == null ? "" : space.getName());
            hit.setLastEditTime(String.valueOf(document.getUpdateTime()));
            records.add(hit);
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    // ------------------------------------------------------------ internal

    /** 授权过滤：空间可见 或 文档级授权（服务端过滤，禁止先查全量） */
    private void applyVisibilityFilter(LambdaQueryWrapper<OaDocument> wrapper,
                                       Set<Long> spaceIds, Set<Long> aclDocIds) {
        boolean hasSpaces = spaceIds != null && !spaceIds.isEmpty();
        boolean hasAclDocs = aclDocIds != null && !aclDocIds.isEmpty();
        if (!hasSpaces && !hasAclDocs) {
            wrapper.eq(OaDocument::getId, -1L);
            return;
        }
        wrapper.and(w -> {
            if (hasSpaces) {
                w.in(OaDocument::getSpaceId, spaceIds);
            }
            if (hasAclDocs) {
                if (hasSpaces) {
                    w.or();
                }
                w.in(OaDocument::getId, aclDocIds);
            }
        });
    }

    private PageVo<DocumentVo> pageDocuments(LambdaQueryWrapper<OaDocument> wrapper,
                                             DocumentPageQuery query, Long actorUserId) {
        IPage<OaDocument> result = documentMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<DocumentVo> records = new ArrayList<>();
        for (OaDocument document : result.getRecords()) {
            records.add(toVo(document, actorUserId, false));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    /** 版本历史只追加：document_id + version 唯一，永不 UPDATE */
    private void appendVersion(OaDocument document, Long actorUserId, String changeSummary) {
        OaDocumentVersion version = new OaDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersion(document.getVersion());
        version.setTitle(document.getTitle());
        version.setContent(document.getContent());
        version.setChangeSummary(changeSummary);
        version.setCreateBy(actorUserId);
        version.setCreateTime(new Date());
        versionMapper.insert(version);
    }

    private OaDocument require(Long id) {
        OaDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new ServiceException("KN_NOT_FOUND 文档不存在", 404);
        }
        return document;
    }

    private OaDocumentVersion requireVersion(Long documentId, Integer version) {
        OaDocumentVersion snapshot = versionMapper.selectOne(new LambdaQueryWrapper<OaDocumentVersion>()
            .eq(OaDocumentVersion::getDocumentId, documentId)
            .eq(OaDocumentVersion::getVersion, version));
        if (snapshot == null) {
            throw new ServiceException("KN_NOT_FOUND 版本不存在", 404);
        }
        return snapshot;
    }

    private DocumentVo toVo(OaDocument document, Long actorUserId, boolean withContent) {
        DocumentVo vo = new DocumentVo();
        vo.setId(document.getId());
        vo.setSpaceId(document.getSpaceId());
        OaKnowledgeSpace space = spaceMapper.selectById(document.getSpaceId());
        vo.setSpaceName(space == null ? "" : space.getName());
        vo.setParentId(document.getParentId());
        vo.setTitle(document.getTitle());
        vo.setContent(withContent ? document.getContent() : null);
        vo.setTags(document.getTags());
        vo.setDocType(document.getDocType());
        vo.setVersion(document.getVersion());
        vo.setStatus(document.getStatus());
        vo.setIsTop(document.getIsTop());
        vo.setViewCount(document.getViewCount());
        vo.setDeletedAt(document.getDeletedAt() == null ? null : String.valueOf(document.getDeletedAt()));
        SpaceRole role = authorization.documentRole(actorUserId, document);
        vo.setMyRole(role == null ? null : role.code());
        vo.setLastEditByName(identityMapper.selectNickName(document.getLastEditBy()));
        vo.setLastEditTime(document.getLastEditTime() == null ? null : String.valueOf(document.getLastEditTime()));
        vo.setCreateBy(document.getCreateBy());
        vo.setCreateByName(identityMapper.selectNickName(document.getCreateBy()));
        vo.setCreateTime(String.valueOf(document.getCreateTime()));
        vo.setUpdateTime(document.getUpdateTime() == null ? null : String.valueOf(document.getUpdateTime()));
        vo.setHasDraft(withContent && revisionMapper.selectCount(new LambdaQueryWrapper<OaDocumentRevision>()
            .eq(OaDocumentRevision::getDocumentId, document.getId())
            .eq(OaDocumentRevision::getUserId, actorUserId)) > 0);
        return vo;
    }

    private DocumentVersionVo toVo(OaDocumentVersion version, boolean withContent) {
        DocumentVersionVo vo = new DocumentVersionVo();
        vo.setId(version.getId());
        vo.setDocumentId(version.getDocumentId());
        vo.setVersion(version.getVersion());
        vo.setTitle(version.getTitle());
        vo.setContent(withContent ? version.getContent() : null);
        vo.setChangeSummary(version.getChangeSummary());
        vo.setCreateBy(version.getCreateBy());
        vo.setCreateByName(identityMapper.selectNickName(version.getCreateBy()));
        vo.setCreateTime(String.valueOf(version.getCreateTime()));
        return vo;
    }
}
