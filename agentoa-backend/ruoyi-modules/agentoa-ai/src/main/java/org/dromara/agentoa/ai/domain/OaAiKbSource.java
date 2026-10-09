package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * AI 知识域数据源 oa_ai_kb_source（docs/21 AI-M3-02）：知识文档或直传文件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_kb_source")
public class OaAiKbSource extends TenantEntity {

    public static final String TYPE_DOCUMENT = "document";
    public static final String TYPE_FILE = "file";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_INDEXING = "indexing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    @TableId(value = "id")
    private Long id;

    private Long kbId;

    /** document/file */
    private String sourceType;

    /** 知识文档 ID（oa_document） */
    private Long docId;

    /** 直传文件 ID（sys_file） */
    private Long fileId;

    private String title;

    private Integer chunkCount;

    /** pending/indexing/ready/failed */
    private String indexStatus;

    private String errorMsg;

    private Date indexedAt;

    private String remark;
}
