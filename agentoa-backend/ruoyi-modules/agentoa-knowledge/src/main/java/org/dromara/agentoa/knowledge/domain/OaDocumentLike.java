package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档点赞 oa_document_like（P1，KB-06）。(document_id,user_id) 唯一，重复点赞不重复计数。
 */
@Data
@TableName("oa_document_like")
public class OaDocumentLike {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    private Long userId;

    private Date createTime;
}
