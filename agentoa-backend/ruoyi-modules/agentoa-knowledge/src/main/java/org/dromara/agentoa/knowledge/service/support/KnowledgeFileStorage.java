package org.dromara.agentoa.knowledge.service.support;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识库文件存储：复用 sys_file 元数据与私有 S3 桶（docs/16 步骤 4），
 * 业务绑定、下载授权与回收站由 oa_document_file 承担。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeFileStorage {

    /** P0 单文件上限 20 MiB（docs/02 KB-04，与代理限制一致） */
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final List<String> ALLOWED_EXT = List.of("pdf", "png", "jpg", "jpeg", "txt", "md");

    private final S3Client s3;
    private final JdbcTemplate jdbc;

    @Value("${agentoa.storage.bucket}")
    private String bucket;

    public record StoredObject(long fileId, String bucket, String objectKey, String fileName,
                               String contentType, long sizeBytes) {
    }

    /** 上传并登记 sys_file；内容、扩展名与 MIME 一致才接受 */
    public StoredObject upload(String rawName, byte[] bytes, Long ownerUserId) {
        String name = Objects.requireNonNullElse(rawName, "file").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.length() > 200) {
            throw new org.dromara.common.core.exception.ServiceException("KN_FILE_INVALID 文件名非法", 400);
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new org.dromara.common.core.exception.ServiceException("KN_FILE_SIZE 文件必须为 1 字节到 20 MiB", 400);
        }
        String type = verifiedType(name, bytes);
        long id = IdUtil.getSnowflakeNextId();
        String key = "private/" + UUID.randomUUID();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),
            RequestBody.fromBytes(bytes));
        try {
            jdbc.update("INSERT INTO sys_file(id,bucket,object_key,original_name,content_type,size_bytes,sha256,owner_user_id) VALUES(?,?,?,?,?,?,?,?)",
                id, bucket, key, name, type, bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), ownerUserId);
        } catch (java.security.NoSuchAlgorithmException e) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw e;
        }
        return new StoredObject(id, bucket, key, name, type, bytes.length);
    }

    /** 按 sys_file 元数据读取私有对象 */
    public byte[] read(long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bucket, object_key FROM sys_file WHERE id = ?", fileId);
        if (rows.isEmpty()) {
            throw new org.dromara.common.core.exception.ServiceException("KN_NOT_FOUND 文件不存在", 404);
        }
        Map<String, Object> file = rows.get(0);
        return s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(file.get("bucket").toString())
            .key(file.get("object_key").toString()).build()).asByteArray();
    }

    static String verifiedType(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new org.dromara.common.core.exception.ServiceException("KN_FILE_TYPE 仅支持 PDF/PNG/JPEG/TXT/MD 文件", 400);
        }
        if ("pdf".equals(ext) && bytes.length >= 5
            && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        if ("png".equals(ext) && bytes.length >= 8
            && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            return "image/png";
        }
        if (("jpg".equals(ext) || "jpeg".equals(ext)) && bytes.length >= 3
            && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255) {
            return "image/jpeg";
        }
        if ("txt".equals(ext) || "md".equals(ext)) {
            if (new String(bytes, StandardCharsets.UTF_8).indexOf('\0') < 0) {
                return "text/plain";
            }
        }
        throw new org.dromara.common.core.exception.ServiceException("KN_FILE_TYPE 文件内容与扩展名不匹配", 400);
    }
}
