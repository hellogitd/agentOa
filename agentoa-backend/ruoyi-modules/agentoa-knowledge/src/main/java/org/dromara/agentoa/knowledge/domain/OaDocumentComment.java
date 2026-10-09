package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 文档评论 oa_document_comment（P1，KB-06）。parentId=0 为一级评论，回复指向父评论。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_document_comment")
public class OaDocumentComment extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    /** 父评论 ID（0=一级评论） */
    private Long parentId;

    private String content;
}
