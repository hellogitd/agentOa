package org.dromara.agentoa.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.FilePageQuery;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.FileVo;
import org.dromara.agentoa.knowledge.service.IKnowledgeFileService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 知识库文件柜接口（docs/16：上传复用 sys_file，下载鉴权 + 审计，删除进回收站）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeFileController {

    private final IKnowledgeFileService fileService;

    @GetMapping("/files")
    public R<PageVo<FileVo>> list(FilePageQuery query) {
        return R.ok(fileService.list(query, LoginHelper.getUserId()));
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_FILE_UPLOAD)
    @Log(title = "知识库文件上传", isSaveRequestData = false, isSaveResponseData = false)
    @RepeatSubmit()
    @PostMapping(value = "/files", consumes = "multipart/form-data")
    public R<FileVo> upload(@RequestParam Long spaceId,
                            @RequestParam(required = false) Long documentId,
                            @RequestPart("file") MultipartFile file) {
        return R.ok(fileService.upload(spaceId, documentId, file, LoginHelper.getUserId()));
    }

    /** 文档附件上传（docs/16 POST /documents/{id}/files） */
    @SaCheckPermission(KnowledgeAccessPolicy.PERM_FILE_UPLOAD)
    @Log(title = "知识库文档附件上传", isSaveRequestData = false, isSaveResponseData = false)
    @RepeatSubmit()
    @PostMapping(value = "/documents/{id}/files", consumes = "multipart/form-data")
    public R<FileVo> uploadForDocument(@PathVariable Long id,
                                       @RequestParam Long spaceId,
                                       @RequestPart("file") MultipartFile file) {
        return R.ok(fileService.upload(spaceId, id, file, LoginHelper.getUserId()));
    }

    @Log(title = "知识库文件下载", isSaveResponseData = false)
    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        IKnowledgeFileService.StoredBlob blob = fileService.download(id, LoginHelper.getUserId());
        return toResponse(blob, true);
    }

    @Log(title = "知识库文件预览", isSaveResponseData = false)
    @GetMapping("/files/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        IKnowledgeFileService.StoredBlob blob = fileService.preview(id, LoginHelper.getUserId());
        return toResponse(blob, false);
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_FILE_REMOVE)
    @Log(title = "知识库文件", businessType = BusinessType.DELETE)
    @DeleteMapping("/files/{id}")
    public R<Void> remove(@PathVariable Long id) {
        fileService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission(KnowledgeAccessPolicy.PERM_DOC_RESTORE)
    @Log(title = "知识库文件恢复", businessType = BusinessType.UPDATE)
    @PostMapping("/files/{id}/restore")
    public R<FileVo> restore(@PathVariable Long id) {
        return R.ok(fileService.restore(id, LoginHelper.getUserId()));
    }

    private ResponseEntity<byte[]> toResponse(IKnowledgeFileService.StoredBlob blob, boolean attachment) {
        MediaType type = MediaType.parseMediaType(
            blob.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : blob.contentType());
        ContentDisposition disposition = (attachment
            ? ContentDisposition.attachment()
            : ContentDisposition.inline())
            .filename(blob.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(blob.bytes());
    }
}
