package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可选办理人（选人组件）：登录即可检索，只回启用账号的最小身份字段。
 * 供 H5/管理端选人组件共用（docs/05 4.6 选人目录）。
 */
@Data
public class AssignableUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 显示名（优先昵称，缺失回退账号名） */
    private String name;

    private String deptName;
}
