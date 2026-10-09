package org.dromara.agentoa.file;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/files")
public class PrivateFileController {
    private final S3Client s3;
    private final JdbcTemplate jdbc;
    @Value("${agentoa.storage.bucket}") private String bucket;
    @GetMapping public R<List<Map<String,Object>>> list() {
        return R.ok(jdbc.queryForList("SELECT CAST(id AS CHAR) AS fileId,original_name AS fileName,size_bytes AS sizeBytes,create_time AS createTime FROM sys_file WHERE owner_user_id=? ORDER BY id DESC LIMIT 100",LoginHelper.getUserId()));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.dromara.common.log.annotation.Log(title="私有文件上传",isSaveRequestData=false,isSaveResponseData=false)
    public R<Map<String,Object>> upload(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be 1 byte to 20 MiB"); }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "file").replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 200) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename too long"); }
        byte[] bytes = file.getBytes();
        String type = verifiedType(name, bytes);
        long id = IdUtil.getSnowflakeNextId();
        String key = "private/" + UUID.randomUUID();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(), RequestBody.fromBytes(bytes));
        try {
            jdbc.update("INSERT INTO sys_file(id,bucket,object_key,original_name,content_type,size_bytes,sha256,owner_user_id) VALUES(?,?,?,?,?,?,?,?)",
                id,bucket,key,name,type,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),LoginHelper.getUserId());
        } catch (RuntimeException e) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw e;
        }
        return R.ok(Map.of("fileId",Long.toString(id),"fileName",name,"sizeBytes",bytes.length));
    }
    @GetMapping("/{id}/download")
    @org.dromara.common.log.annotation.Log(title="私有文件下载",isSaveResponseData=false)
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        var rows = jdbc.queryForList("SELECT * FROM sys_file WHERE id=? AND owner_user_id=?", id, LoginHelper.getUserId());
        if (rows.isEmpty()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found"); }
        var file = rows.get(0);
        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(file.get("bucket").toString()).key(file.get("object_key").toString()).build()).asByteArray();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.get("original_name").toString(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);
    }
    static String verifiedType(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && bytes.length >= 5 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) { return "application/pdf"; }
        if (lower.endsWith(".png") && bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) { return "image/png"; }
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) { return "image/jpeg"; }
        if (lower.endsWith(".txt") && new String(bytes,StandardCharsets.UTF_8).indexOf('\0') < 0) { return "text/plain"; }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only PDF, PNG, JPEG and text files with matching content are accepted");
    }

    @Configuration static class StorageConfig {
        @Bean(destroyMethod="close") S3Client privateStorage(@Value("${agentoa.storage.endpoint}") String endpoint,
            @Value("${agentoa.storage.access-key}") String access, @Value("${agentoa.storage.secret-key}") String secret) {
            return S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret))).build();
        }
    }
}
