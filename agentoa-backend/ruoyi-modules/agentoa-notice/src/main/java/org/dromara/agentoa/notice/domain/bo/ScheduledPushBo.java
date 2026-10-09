package org.dromara.agentoa.notice.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定时推送任务创建/修改入参（NC-04）。
 */
@Data
public class ScheduledPushBo {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称长度不能超过{max}个字符")
    private String name;

    /** 推送类型（1定时公告 2模板推送） */
    @NotNull(message = "推送类型不能为空")
    @Pattern(regexp = "1|2", message = "推送类型取值非法")
    private String pushType;

    /** 定时公告 ID（类型1） */
    private Long announcementId;

    /** 通知模板 ID（类型2） */
    private Long templateId;

    /** 受众范围（类型2） */
    @Pattern(regexp = "1|2|3|4", message = "受众范围取值非法")
    private String scopeType;

    @Size(max = 2000, message = "范围值长度不能超过{max}个字符")
    private String scopeValues;

    /** 模板变量值（类型2） */
    private Map<String, String> vars;

    /** 调度方式（1单次 2周期） */
    @NotNull(message = "调度方式不能为空")
    @Pattern(regexp = "1|2", message = "调度方式取值非法")
    private String scheduleType;

    /** 单次执行时间（调度1） */
    private LocalDateTime runAt;

    /** 周期表达式（调度2，5字段受限子集） */
    @Size(max = 64, message = "周期表达式长度不能超过{max}个字符")
    private String cronExpr;

    @Pattern(regexp = "1|2", message = "状态取值非法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
