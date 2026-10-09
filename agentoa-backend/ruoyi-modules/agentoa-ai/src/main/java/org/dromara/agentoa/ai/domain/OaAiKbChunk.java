package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 知识分块 oa_ai_kb_chunk（docs/21 AI-M3-03/04）：embedding BLOB + 应用内余弦相似度。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_kb_chunk")
public class OaAiKbChunk extends TenantEntity {

    /** 向量存储类型：MySQL BLOB（P0，docs/21 §9.2） */
    public static final String STORE_BLOB = "blob";

    @TableId(value = "id")
    private Long id;

    private Long kbId;

    private Long sourceId;

    /** 块序号（0 起） */
    private Integer seq;

    private String heading;

    private String content;

    private Integer tokenCount;

    /** float32 小端数组 */
    private byte[] embedding;

    /** blob（预留专用向量库切换） */
    private String storeType;

    private String remark;
}
