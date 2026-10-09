package org.dromara.agentoa.notice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公告视图：已发布内容冻结；audienceCount 为受众快照人数。
 */
@Data
public class AnnouncementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String content;

    private String noticeType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long publisherId;

    private String publisherName;

    private String publishTime;

    private String effectiveStart;

    private String effectiveEnd;

    private Integer scopeType;

    private String scopeValues;

    private Integer isTop;

    private Integer isPopup;

    private Integer status;

    private Integer readCount;

    private Integer audienceCount;

    private String attachments;

    /** 当前用户是否已读 */
    private Boolean read;

    private String createTime;

    private String updateTime;
}
