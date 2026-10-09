package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档社交统计视图（P1）：点赞数、是否已点赞、是否已收藏。
 */
@Data
public class SocialStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    private Long likeCount;

    private Boolean likedByMe;

    private Boolean favoritedByMe;
}
