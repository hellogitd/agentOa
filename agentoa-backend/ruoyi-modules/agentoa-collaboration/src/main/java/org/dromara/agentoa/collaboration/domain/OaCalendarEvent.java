package org.dromara.agentoa.collaboration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 日程/会议（docs/04 9.1 / docs/17）：时间存储 UTC，参与人与可见范围控制可见性。
 */
@Data
@TableName("oa_calendar_event")
public class OaCalendarEvent {

    @TableId(value = "id")
    private Long id;

    private Integer lockVersion;

    private String title;

    private String description;

    /** 类型（1日程 2会议） */
    private Integer eventType;

    /** 开始时间（UTC） */
    private Date startTime;

    /** 结束时间（UTC） */
    private Date endTime;

    private Integer isAllDay;

    private String location;

    private Long organizerId;

    private Long roomId;

    /** 可见范围（1私有 2参与人 3部门 4全员） */
    private Integer visibility;

    private Integer remindMinutes;

    /** 重复规则（P1，未启用） */
    private String repeatRule;

    /** 系列主记录 ID（0=非重复） */
    private Long seriesId;

    /** 重复截止日期 */
    private java.util.Date repeatUntil;

    /** 重复次数上限 */
    private Integer repeatCount;

    /** 是否已脱离规则的实例（1=是） */
    private Integer isException;

    /** 状态（1正常 2已取消） */
    private Integer status;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;
}
