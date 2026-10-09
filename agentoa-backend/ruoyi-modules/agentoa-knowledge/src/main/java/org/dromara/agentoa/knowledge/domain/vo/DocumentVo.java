package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档视图：列表省略 content，详情携带正文与个人草稿提示。
 */
@Data
public class DocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long spaceId;

    private String spaceName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String title;

    /** 正文（Markdown，仅详情返回） */
    private String content;

    private String tags;

    private String docType;

    /** 当前版本号 */
    private Integer version;

    /** 状态（1草稿 2已发布 3已归档） */
    private Integer status;

    private Integer isTop;

    private Integer viewCount;

    /** 回收站起算时间（空为未删除） */
    private String deletedAt;

    /** 当前用户有效角色 */
    private String myRole;

    private String lastEditByName;

    private String lastEditTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createBy;

    private String createByName;

    private String createTime;

    private String updateTime;

    /** 当前用户是否有未提交的个人草稿 */
    private Boolean hasDraft;
}
