package org.dromara.agentoa.ai.service.support;

/**
 * 知识域直传文件存储（复用 sys_file + 私有 S3，docs/21 AI-M3-02）。
 */
public interface KbFileStore {

    record StoredFile(long fileId, String fileName, String contentType, long sizeBytes) {
    }

    /** 上传并登记 sys_file（三重校验见 {@link AiDocumentParser#verifiedType}） */
    StoredFile upload(String rawName, byte[] bytes, Long ownerUserId);

    /** 按 ID 读取文件字节（知识域检索场景，索引时读取） */
    byte[] read(long fileId);

    /** 删除存储对象（数据源删除时清理） */
    void delete(long fileId);
}
