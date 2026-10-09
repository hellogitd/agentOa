package org.dromara.agentoa.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentFile;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeSpace;
import org.dromara.agentoa.knowledge.domain.bo.FilePageQuery;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.FileVo;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentFileMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeSpaceMapper;
import org.dromara.agentoa.knowledge.service.IKnowledgeFileService;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.agentoa.knowledge.service.support.KnowledgeFileStorage;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件柜实现：上传绑定空间/文档授权，下载按空间/文档角色鉴权并留审计，删除进回收站。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeFileServiceImpl implements IKnowledgeFileService {

    /** 回收站恢复期限（与文档一致，30 天） */
    static final long RESTORE_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final OaDocumentFileMapper fileMapper;
    private final OaDocumentMapper documentMapper;
    private final OaKnowledgeSpaceMapper spaceMapper;
    private final KnowledgeIdentityMapper identityMapper;
    private final KnowledgeAuthorization authorization;
    private final KnowledgeFileStorage storage;

    @Override
    public PageVo<FileVo> list(FilePageQuery query, Long actorUserId) {
        if (query.getSpaceId() != null) {
            authorization.requireSpaceView(actorUserId, query.getSpaceId());
        }
        if (query.getDocumentId() != null) {
            OaDocument document = documentMapper.selectById(query.getDocumentId());
            if (document == null) {
                throw new ServiceException("KN_NOT_FOUND 文档不存在", 404);
            }
            authorization.requireView(actorUserId, document);
        }
        Set<Long> spaceIds = authorization.authorizedSpaceIds(actorUserId);
        LambdaQueryWrapper<OaDocumentFile> wrapper = new LambdaQueryWrapper<>();
        if (query.getSpaceId() != null) {
            wrapper.eq(OaDocumentFile::getSpaceId, query.getSpaceId());
        } else if (spaceIds == null || spaceIds.isEmpty()) {
            wrapper.eq(OaDocumentFile::getId, -1L);
        } else {
            wrapper.in(OaDocumentFile::getSpaceId, spaceIds);
        }
        if (query.getDocumentId() != null) {
            wrapper.eq(OaDocumentFile::getDocumentId, query.getDocumentId());
        }
        if (query.deletedOnly()) {
            wrapper.isNotNull(OaDocumentFile::getDeletedAt);
        } else {
            wrapper.isNull(OaDocumentFile::getDeletedAt);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaDocumentFile::getFileName, query.getKeyword().trim());
        }
        wrapper.orderByDesc(OaDocumentFile::getCreateTime);
        IPage<OaDocumentFile> result = fileMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<FileVo> records = new ArrayList<>();
        for (OaDocumentFile file : result.getRecords()) {
            records.add(toVo(file, actorUserId));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVo upload(Long spaceId, Long documentId, MultipartFile file, Long actorUserId) {
        authorization.requireSpaceEdit(actorUserId, spaceId);
        SpaceRole spaceRole = authorization.spaceRole(actorUserId, spaceId);
        if (!KnowledgeAccessPolicy.canEdit(spaceRole)) {
            throw new ServiceException("KN_FORBIDDEN 无编辑权限", 403);
        }
        if (documentId != null) {
            OaDocument document = documentMapper.selectById(documentId);
            if (document == null || !document.getSpaceId().equals(spaceId)) {
                throw new ServiceException("KN_NOT_FOUND 文档不存在或不属于该空间", 404);
            }
            if (document.getDeletedAt() != null) {
                throw new ServiceException("KN_STATE_CONFLICT 文档在回收站中，无法上传附件", 409);
            }
            authorization.requireEdit(actorUserId, document);
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("KN_FILE_SIZE 文件不能为空", 400);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("KN_FILE_INVALID 文件读取失败", 400);
        }
        KnowledgeFileStorage.StoredObject stored =
            storage.upload(file.getOriginalFilename(), bytes, actorUserId);
        OaDocumentFile record = new OaDocumentFile();
        record.setSpaceId(spaceId);
        record.setDocumentId(documentId);
        record.setFileId(stored.fileId());
        record.setFileName(stored.fileName());
        record.setFileExt(extension(stored.fileName()));
        record.setFileSize(stored.sizeBytes());
        record.setContentType(stored.contentType());
        record.setDownloadCount(0);
        record.setCreateBy(actorUserId);
        record.setCreateTime(new Date());
        fileMapper.insert(record);
        return toVo(record, actorUserId);
    }

    @Override
    public StoredBlob download(Long id, Long actorUserId) {
        OaDocumentFile record = require(id);
        requireDownloadRole(record, actorUserId);
        markDownloaded(id, actorUserId);
        byte[] bytes = storage.read(record.getFileId());
        return new StoredBlob(record.getFileName(), record.getContentType(), bytes);
    }

    @Override
    public StoredBlob preview(Long id, Long actorUserId) {
        OaDocumentFile record = require(id);
        requireDownloadRole(record, actorUserId);
        String type = record.getContentType() == null ? "" : record.getContentType();
        boolean previewable = type.startsWith("image/") || type.equals("application/pdf") || type.equals("text/plain");
        if (!previewable) {
            throw new ServiceException("KN_FILE_TYPE 该文件类型不支持预览", 400);
        }
        markDownloaded(id, actorUserId);
        byte[] bytes = storage.read(record.getFileId());
        return new StoredBlob(record.getFileName(), record.getContentType(), bytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaDocumentFile record = require(id);
        if (record.getDeletedAt() != null) {
            return;
        }
        requireFileEdit(record, actorUserId);
        Date now = new Date();
        fileMapper.update(null, new LambdaUpdateWrapper<OaDocumentFile>()
            .eq(OaDocumentFile::getId, id)
            .isNull(OaDocumentFile::getDeletedAt)
            .set(OaDocumentFile::getDeletedAt, now)
            .set(OaDocumentFile::getDeletedBy, actorUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVo restore(Long id, Long actorUserId) {
        OaDocumentFile record = require(id);
        if (record.getDeletedAt() == null) {
            throw new ServiceException("KN_STATE_CONFLICT 文件不在回收站", 409);
        }
        if (System.currentTimeMillis() - record.getDeletedAt().getTime() > RESTORE_WINDOW_MILLIS) {
            throw new ServiceException("KN_STATE_CONFLICT 已超过 30 天恢复期限", 409);
        }
        requireFileEdit(record, actorUserId);
        fileMapper.update(null, new LambdaUpdateWrapper<OaDocumentFile>()
            .eq(OaDocumentFile::getId, id)
            .set(OaDocumentFile::getDeletedAt, null)
            .set(OaDocumentFile::getDeletedBy, null));
        record.setDeletedAt(null);
        record.setDeletedBy(null);
        return toVo(record, actorUserId);
    }

    // ------------------------------------------------------------ internal

    private void requireDownloadRole(OaDocumentFile record, Long actorUserId) {
        SpaceRole role = authorization.spaceRole(actorUserId, record.getSpaceId());
        if (record.getDocumentId() != null) {
            OaDocument document = documentMapper.selectById(record.getDocumentId());
            if (document != null) {
                role = KnowledgeAccessPolicy.effectiveRole(role, authorization.documentRole(actorUserId, document));
            }
        }
        if (!KnowledgeAccessPolicy.canDownload(role)) {
            throw new ServiceException("KN_NOT_FOUND 文件不存在或无权下载", 404);
        }
    }

    private void requireFileEdit(OaDocumentFile record, Long actorUserId) {
        SpaceRole role = authorization.spaceRole(actorUserId, record.getSpaceId());
        if (!KnowledgeAccessPolicy.canEdit(role)) {
            throw new ServiceException("KN_FORBIDDEN 无编辑权限", 403);
        }
    }

    private void markDownloaded(Long id, Long actorUserId) {
        fileMapper.update(null, new LambdaUpdateWrapper<OaDocumentFile>()
            .eq(OaDocumentFile::getId, id)
            .setSql("download_count = download_count + 1")
            .set(OaDocumentFile::getLastDownloadBy, actorUserId)
            .set(OaDocumentFile::getLastDownloadTime, new Date()));
    }

    private OaDocumentFile require(Long id) {
        OaDocumentFile record = fileMapper.selectById(id);
        if (record == null) {
            throw new ServiceException("KN_NOT_FOUND 文件不存在", 404);
        }
        return record;
    }

    private String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private FileVo toVo(OaDocumentFile record, Long actorUserId) {
        FileVo vo = new FileVo();
        vo.setId(record.getId());
        vo.setSpaceId(record.getSpaceId());
        OaKnowledgeSpace space = spaceMapper.selectById(record.getSpaceId());
        vo.setSpaceName(space == null ? "" : space.getName());
        vo.setDocumentId(record.getDocumentId());
        if (record.getDocumentId() != null) {
            OaDocument document = documentMapper.selectById(record.getDocumentId());
            vo.setDocumentTitle(document == null ? "" : document.getTitle());
        }
        vo.setFileId(record.getFileId());
        vo.setFileName(record.getFileName());
        vo.setFileExt(record.getFileExt());
        vo.setFileSize(record.getFileSize());
        vo.setContentType(record.getContentType());
        vo.setDownloadCount(record.getDownloadCount());
        vo.setDeletedAt(record.getDeletedAt() == null ? null : String.valueOf(record.getDeletedAt()));
        SpaceRole role = authorization.spaceRole(actorUserId, record.getSpaceId());
        vo.setMyRole(role == null ? null : role.code());
        vo.setCreateBy(record.getCreateBy());
        vo.setCreateByName(identityMapper.selectNickName(record.getCreateBy()));
        vo.setCreateTime(String.valueOf(record.getCreateTime()));
        return vo;
    }
}
