package org.dromara.agentoa.ai.service.support;

import cn.hutool.core.util.IdUtil;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 直传文件存储实现：sys_file 元数据 + 私有 S3 桶（口径同对话附件）。
 */
@Component
public class S3KbFileStore implements KbFileStore {

    private final S3Client s3;
    private final JdbcTemplate jdbc;

    @Value("${agentoa.storage.bucket}")
    private String bucket;

    public S3KbFileStore(S3Client s3, JdbcTemplate jdbc) {
        this.s3 = s3;
        this.jdbc = jdbc;
    }

    @Override
    public StoredFile upload(String rawName, byte[] bytes, Long ownerUserId) {
        String name = Objects.requireNonNullElse(rawName, "file").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.length() > 200) {
            throw new ServiceException("AI_KB_FILE_NAME 文件名非法", 400);
        }
        String ext = AiDocumentParser.verifiedType(name, bytes);
        String type = switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "md" -> "text/markdown";
            default -> "text/plain";
        };
        long id = IdUtil.getSnowflakeNextId();
        String key = "private/" + UUID.randomUUID();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),
            RequestBody.fromBytes(bytes));
        try {
            jdbc.update("INSERT INTO sys_file(id,bucket,object_key,original_name,content_type,size_bytes,sha256,owner_user_id) VALUES(?,?,?,?,?,?,?,?)",
                id, bucket, key, name, type, bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), ownerUserId);
        } catch (Exception e) {
            s3.deleteObject(builder -> builder.bucket(bucket).key(key));
            if (e instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new IllegalStateException(e);
        }
        return new StoredFile(id, name, type, bytes.length);
    }

    @Override
    public byte[] read(long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bucket, object_key FROM sys_file WHERE id = ?", fileId);
        if (rows.isEmpty()) {
            throw new ServiceException("AI_KB_FILE_NOT_FOUND 文件不存在", 404);
        }
        Map<String, Object> row = rows.get(0);
        return s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(String.valueOf(row.get("bucket")))
            .key(String.valueOf(row.get("object_key"))).build()).asByteArray();
    }

    @Override
    public void delete(long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bucket, object_key FROM sys_file WHERE id = ?", fileId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        try {
            s3.deleteObject(builder -> builder.bucket(String.valueOf(row.get("bucket")))
                .key(String.valueOf(row.get("object_key"))));
        } catch (RuntimeException ignored) {
            // 存储清理失败不影响业务删除
        }
        jdbc.update("DELETE FROM sys_file WHERE id = ?", fileId);
    }
}
