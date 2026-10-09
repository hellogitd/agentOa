package org.dromara.agentoa.notice.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时推送任务 oa_scheduled_push（NC-04）：定时公告 / 模板推送，DB 租约调度。
 */
@Data
@TableName("oa_scheduled_push")
public class OaScheduledPush {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_PAUSED = 2;

    @TableId(value = "id")
    private Long id;

    private String name;

    /** 推送类型（1定时公告 2模板推送） */
    private Integer pushType;

    private Long announcementId;

    private Long templateId;

    /** 受众范围（1全员 2部门 3角色 4指定人，类型2） */
    private Integer scopeType;

    private String scopeValues;

    /** 模板变量值（JSON 对象字符串，类型2） */
    private String varsJson;

    /** 调度方式（1单次 2周期cron子集） */
    private Integer scheduleType;

    private LocalDateTime runAt;

    private String cronExpr;

    /** 下次执行时间（NULL=无待执行） */
    private LocalDateTime nextRunTime;

    private LocalDateTime lastRunTime;

    private Integer runCount;

    private String lastError;

    /** 状态（1启用 2暂停） */
    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String remark;
}
