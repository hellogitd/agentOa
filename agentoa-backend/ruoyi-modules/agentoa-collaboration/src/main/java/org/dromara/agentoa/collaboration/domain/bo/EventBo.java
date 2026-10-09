package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 日程创建/修改入参（docs/05 9.1，时间点为 RFC 3339） */
@Data
public class EventBo {

    /** 更新必填：乐观锁版本（docs/05 1.7） */
    private Integer lockVersion;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    private String description;

    /** 类型（1日程 2会议） */
    @Pattern(regexp = "1|2", message = "类型取值非法")
    private String eventType;

    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    private Integer isAllDay;

    @Size(max = 255, message = "地点长度不能超过{max}个字符")
    private String location;

    /** 会议室ID（会议可选） */
    private String roomId;

    /** 可见范围（1私有 2参与人 3部门 4全员） */
    @Pattern(regexp = "1|2|3|4", message = "可见范围取值非法")
    private String visibility;

    private Integer remindMinutes;

    /** 参与人账号ID */

    /** 重复规则（RRULE 子集，如 FREQ=WEEKLY;BYDAY=MO;COUNT=10） */
    private String repeatRule;

    /** 重复截止日期（RFC 3339） */
    private String repeatUntil;

    /** 重复次数 */
    private Integer repeatCount;
    private List<String> attendeeIds;
}
