package org.dromara.agentoa.notice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * outbox 事件视图（失败事件查询与人工重投）。
 */
@Data
public class OutboxEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String eventId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    private String eventType;

    private String title;

    private String status;

    private Integer retryCount;

    private Integer redeliverCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long redeliveredBy;

    private String redeliveredTime;

    private String nextAttemptTime;

    private String lastError;

    /** 原始 payload JSON（用于提取标题） */
    private String payload;

    private String createTime;

    private String processedTime;
}
