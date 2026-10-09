package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 知识域成员视图（docs/21 AI-M3-01）。
 */
@Data
public class AiKbMemberVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userName;

    private String nickName;
}
