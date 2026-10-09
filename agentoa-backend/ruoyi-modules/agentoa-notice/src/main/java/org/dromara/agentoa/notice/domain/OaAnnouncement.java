package org.dromara.agentoa.notice.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 公告（docs/04 7.1 / docs/15）：发布时冻结标题、正文、附件与受众快照。
 */
@Data
@TableName("oa_announcement")
public class OaAnnouncement {

    @TableId(value = "id")
    private Long id;

    private String title;

    private String content;

    /** 公告类型（company/department/hr/admin） */
    private String noticeType;

    private Long publisherId;

    private Date publishTime;

    private Date effectiveStart;

    private Date effectiveEnd;

    /** 范围（1全员 2部门 3角色 4指定人） */
    private Integer scopeType;

    /** 范围值（部门ID/角色key/用户ID，逗号分隔） */
    private String scopeValues;

    private Integer isTop;

    private Integer isPopup;

    /** 状态（1草稿 2已发布 3已撤回 4已归档） */
    private Integer status;

    private Integer readCount;

    /** 附件列表（发布时冻结） */
    private String attachments;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String remark;
}
