package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识空间视图：包含当前用户在空间上的有效角色。
 */
@Data
public class SpaceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String icon;

    private String description;

    /** 空间类型（1公开 2私密 3团队） */
    private Integer spaceType;

    /** 当前用户有效角色（OWNER/EDITOR/COMMENTER/VIEWER，空为无权限） */
    private String myRole;

    private Integer memberCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createBy;

    private String createByName;

    private String createTime;

    private String updateTime;

    private String remark;
}
