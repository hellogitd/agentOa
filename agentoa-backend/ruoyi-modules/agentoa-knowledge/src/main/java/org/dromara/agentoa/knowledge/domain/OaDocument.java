package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档（docs/16）：Markdown 正文、版本基线、发布/归档状态与回收站标记。
 */
@Data
@TableName("oa_document")
public class OaDocument {

    @TableId(value = "id")
    private Long id;

    private Long spaceId;

    /** 父文档ID（0 为根，形成目录树） */
    private Long parentId;

    /** 节点类型（1文档 2目录） */
    private Integer nodeType;

    private String title;

    /** 正文（Markdown） */
    private String content;

    /** 纯文本（搜索用） */
    private String contentText;

    private String docType;

    /** 标签（逗号分隔） */
    private String tags;

    /** 当前版本号（乐观锁基线） */
    private Integer version;

    /** 状态（1草稿 2已发布 3已归档） */
    private Integer status;

    private Integer isTop;

    private Integer viewCount;

    /** 回收站起算时间（NULL 未删除） */
    private Date deletedAt;

    /** 删除操作账号ID（审计） */
    private Long deletedBy;

    private Long lastEditBy;

    private Date lastEditTime;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String remark;
}
