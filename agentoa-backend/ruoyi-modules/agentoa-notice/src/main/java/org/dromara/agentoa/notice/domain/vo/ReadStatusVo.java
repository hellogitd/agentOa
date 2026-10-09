package org.dromara.agentoa.notice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 公告已读/未读统计（分母为受众快照人数）。
 */
@Data
public class ReadStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long noticeId;

    private Integer audienceCount;

    private Integer readCount;

    private List<UserBrief> readUsers;

    private List<UserBrief> unreadUsers;

    @Data
    public static class UserBrief implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;

        private String nickname;

        private String readTime;
    }
}
