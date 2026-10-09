package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文档级授权（docs/16）：与空间角色取较高者；授权记录保留授权人与时间用于审计。
 */
@Data
@TableName("oa_document_acl")
public class OaDocumentAcl {

    @TableId(value = "id")
    private Long id;

    private Long documentId;

    /** 主体类型（USER/ROLE） */
    private String subjectType;

    /** 主体ID（账号ID/角色ID） */
    private Long subjectId;

    /** 授权角色（EDITOR/COMMENTER/VIEWER） */
    private String role;

    /** 目录覆盖开关（1=仅本目录 ACL 生效） */
    private Integer overrideOnly;

    /** 授权人账号ID（审计） */
    private Long grantedBy;

    /** 授权时间（审计） */
    private Date grantedTime;
}
