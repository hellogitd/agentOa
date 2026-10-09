package org.dromara.agentoa.reporting.service.support;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 导出文件私有存储（docs/18 步骤 3：大导出使用异步任务和私有文件）。
 * 内容由服务端生成，复用 sys_file 元数据与私有 S3 桶；下载走 /api/v1/files/{id}/download 的属主校验。
 */
@Component
@RequiredArgsConstructor
public class S3ExportFileStore implements ExportFileStore {

    /** 单次导出文件上限 20 MiB，与私有存储限制一致。 */
    public static final long MAX_EXPORT_BYTES = 20L * 1024 * 1024;

    private final S3Client s3;
    private final JdbcTemplate jdbc;

    @Value("${agentoa.storage.bucket}")
    private String bucket;

    @Override
    public long store(String fileName, byte[] content, Long ownerUserId) {
        String name = Objects.requireNonNullElse(fileName, "export").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (content == null || content.length == 0) {
            throw new ServiceException("RP_EXPORT_EMPTY 导出内容为空", 400);
        }
        if (content.length > MAX_EXPORT_BYTES) {
            throw new ServiceException("RP_EXPORT_SIZE 导出文件超过 20 MiB 上限，请缩小区间或分页导出", 400);
        }
        long id = IdUtil.getSnowflakeNextId();
        String key = "private/" + UUID.randomUUID();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
            .contentType("application/octet-stream").build(), RequestBody.fromBytes(content));
        try {
            jdbc.update("INSERT INTO sys_file(id,bucket,object_key,original_name,content_type,size_bytes,sha256,owner_user_id) "
                    + "VALUES(?,?,?,?,?,?,?,?)",
                id, bucket, key, name, "application/octet-stream", content.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), ownerUserId);
        } catch (Exception e) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw e instanceof ServiceException serviceException
                ? serviceException : new ServiceException("RP_EXPORT_STORE 导出文件保存失败", 500);
        }
        return id;
    }
}
