package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识空间成员（docs/16）：授权记录保留授权人与授权时间用于审计。
 */
@Data
@TableName("oa_knowledge_member")
public class OaKnowledgeMember {

    @TableId(value = "id")
    private Long id;

    private Long spaceId;

    private Long userId;

    /** 空间角色（OWNER/EDITOR/COMMENTER/VIEWER） */
    private String role;

    /** 授权人账号ID（审计） */
    private Long grantedBy;

    /** 授权时间（审计） */
    private Date grantedTime;

    private Date createTime;
}
