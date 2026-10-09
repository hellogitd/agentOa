package org.dromara.agentoa.collaboration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 日程视图：时间点为 RFC 3339 UTC；参与人带邀请响应状态 */
@Data
public class EventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer lockVersion;

    private String title;

    private String description;

    private Integer eventType;

    private String startTime;

    private String endTime;

    private Integer isAllDay;

    private String location;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizerId;

    private String organizerName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    private String roomName;

    private Integer visibility;

    private Integer remindMinutes;

    private Integer status;

    /** 重复规则 */
    private String repeatRule;

    /** 系列主记录 ID（0=非重复） */
    private Long seriesId;

    /** 是否已脱离规则的实例 */
    private Integer isException;

    private List<Attendee> attendees = new ArrayList<>();

    /** 当前用户对邀请的响应（组织者为 ACCEPTED，未参与为 null） */
    private String myResponse;

    /** 当前用户是否可修改/删除/取消 */
    private Boolean canManage;

    private String createTime;

    private String updateTime;

    @Data
    public static class Attendee implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;

        private String nickname;

        private String responseStatus;
    }
}
