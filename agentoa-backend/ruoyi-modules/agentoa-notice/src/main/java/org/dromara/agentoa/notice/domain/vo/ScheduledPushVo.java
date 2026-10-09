package org.dromara.agentoa.notice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定时推送任务视图（NC-04）。
 */
@Data
public class ScheduledPushVo {

    private Long id;

    private String name;

    private Integer pushType;

    private Long announcementId;

    private String announcementTitle;

    private Long templateId;

    private String templateCode;

    private String templateName;

    private Integer scopeType;

    private String scopeValues;

    private Map<String, String> vars;

    private Integer scheduleType;

    private LocalDateTime runAt;

    private String cronExpr;

    private LocalDateTime nextRunTime;

    private LocalDateTime lastRunTime;

    private Integer runCount;

    private String lastError;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;
}
