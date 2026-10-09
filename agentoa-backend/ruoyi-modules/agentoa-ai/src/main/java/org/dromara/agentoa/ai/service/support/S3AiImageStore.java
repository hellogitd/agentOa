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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 图片附件存储实现：sys_file 元数据 + 私有 S3 桶（口径同知识库文件，docs/21 AI-M2-04）。
 */
@Component
public class S3AiImageStore implements AiImageStore {

    private final S3Client s3;
    private final JdbcTemplate jdbc;

    @Value("${agentoa.storage.bucket}")
    private String bucket;

    public S3AiImageStore(S3Client s3, JdbcTemplate jdbc) {
        this.s3 = s3;
        this.jdbc = jdbc;
    }

    @Override
    public StoredImage upload(String rawName, byte[] bytes, Long ownerUserId) {
        String name = Objects.requireNonNullElse(rawName, "image").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.length() > 200) {
            throw new ServiceException("AI_ATTACHMENT_NAME 文件名非法", 400);
        }
        String type = AiAttachmentRules.verifiedType(name, bytes);
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
        return new StoredImage(id, name, type, bytes.length);
    }

    @Override
    public LoadedImage read(long fileId, Long ownerUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bucket, object_key, content_type FROM sys_file WHERE id = ? AND owner_user_id = ?", fileId, ownerUserId);
        if (rows.isEmpty()) {
            throw new ServiceException("AI_ATTACHMENT_NOT_FOUND 图片不存在", 404);
        }
        Map<String, Object> row = rows.get(0);
        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(String.valueOf(row.get("bucket")))
            .key(String.valueOf(row.get("object_key"))).build()).asByteArray();
        String contentType = row.get("content_type") == null ? "image/png" : String.valueOf(row.get("content_type"));
        return new LoadedImage(contentType.toLowerCase(Locale.ROOT), bytes);
    }
}
