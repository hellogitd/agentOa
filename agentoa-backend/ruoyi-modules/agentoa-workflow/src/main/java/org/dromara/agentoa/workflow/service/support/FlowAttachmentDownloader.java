package org.dromara.agentoa.workflow.service.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.util.List;
import java.util.Map;

/**
 * 流程附件字节读取：sys_file 元数据 + 私有 S3 桶（口径同 finance 发票图 / AI 图片，docs/23 H5-H3-01）。
 * 授权由调用方（实例可见性 + 表单引用）判定，本类只做存储读取。
 */
@Component
public class FlowAttachmentDownloader {

    private final S3Client s3;
    private final JdbcTemplate jdbc;

    public FlowAttachmentDownloader(S3Client s3, JdbcTemplate jdbc) {
        this.s3 = s3;
        this.jdbc = jdbc;
    }

    /** 文件不存在返回 null。 */
    public byte[] load(Long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bucket,object_key,original_name,content_type FROM sys_file WHERE id = ?", fileId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> file = rows.get(0);
        return s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(String.valueOf(file.get("bucket")))
            .key(String.valueOf(file.get("object_key")))
            .build()).asByteArray();
    }

    public String originalName(Long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT original_name FROM sys_file WHERE id = ?", fileId);
        return rows.isEmpty() ? "attachment" : String.valueOf(rows.get(0).get("original_name"));
    }
}
