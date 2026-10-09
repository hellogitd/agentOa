package org.dromara.agentoa.ai.service.support;

/**
 * 对话图片附件存储（复用 sys_file + 私有 S3，docs/21 AI-M2-04）。
 */
public interface AiImageStore {

    record StoredImage(long fileId, String fileName, String contentType, long sizeBytes) {
    }

    record LoadedImage(String mimeType, byte[] bytes) {
    }

    /** 上传并登记 sys_file（内容校验见 {@link AiAttachmentRules}） */
    StoredImage upload(String rawName, byte[] bytes, Long ownerUserId);

    /** 按归属读取图片（越权即 404） */
    LoadedImage read(long fileId, Long ownerUserId);
}
