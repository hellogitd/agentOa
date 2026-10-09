package org.dromara.agentoa.knowledge.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 空间成员添加/改角色载荷（docs/16 成员角色 OWNER/EDITOR/COMMENTER/VIEWER）。
 */
@Data
public class MemberBo {

    @NotNull(message = "账号ID不能为空")
    private Long userId;

    @NotNull(message = "成员角色不能为空")
    @Pattern(regexp = "OWNER|EDITOR|COMMENTER|VIEWER", message = "成员角色取值非法")
    private String role;
}
