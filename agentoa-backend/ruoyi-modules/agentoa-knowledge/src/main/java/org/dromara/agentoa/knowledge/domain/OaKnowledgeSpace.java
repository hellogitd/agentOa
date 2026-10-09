package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识空间（docs/16）：可见性来自成员授权或空间类型，管理员也需要显式授权。
 */
@Data
@TableName("oa_knowledge_space")
public class OaKnowledgeSpace {

    @TableId(value = "id")
    private Long id;

    private String name;

    private String icon;

    private String description;

    /** 空间类型（1公开 2私密 3团队） */
    private Integer spaceType;

    private Long createDept;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String remark;
}
