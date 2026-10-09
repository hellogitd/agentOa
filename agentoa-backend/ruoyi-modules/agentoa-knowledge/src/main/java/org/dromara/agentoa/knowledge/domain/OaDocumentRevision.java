package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档个人草稿（docs/16）：30 秒自动保存的编辑副本，携带 baseVersion 用于并发冲突检测。
 */
@Data
@TableName("oa_document_revision")
public class OaDocumentRevision {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    private Long userId;

    /** 编辑时基线版本 */
    private Integer baseVersion;

    private String title;

    private String content;

    private Date updateTime;
}
