package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档收藏 oa_document_favorite（P1，KB-07）。(document_id,user_id) 唯一。
 */
@Data
@TableName("oa_document_favorite")
public class OaDocumentFavorite {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    private Long userId;

    private Date createTime;
}
