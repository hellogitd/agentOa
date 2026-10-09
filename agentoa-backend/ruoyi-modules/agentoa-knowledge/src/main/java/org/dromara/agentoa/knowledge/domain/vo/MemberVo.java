package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 空间成员视图（含授权审计信息）。
 */
@Data
public class MemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long spaceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    /** 空间角色（OWNER/EDITOR/COMMENTER/VIEWER） */
    private String role;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long grantedBy;

    private String grantedByName;

    private String grantedTime;
}
