package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档版本历史（docs/16）：只追加，不可覆盖（document_id + version 唯一）。
 */
@Data
@TableName("oa_document_version")
public class OaDocumentVersion {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    private Integer version;

    private String title;

    private String content;

    private String changeSummary;

    private Long createBy;

    private Date createTime;
}
