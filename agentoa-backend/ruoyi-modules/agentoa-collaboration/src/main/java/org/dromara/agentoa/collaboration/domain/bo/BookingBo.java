package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 会议室预约入参（docs/05 9.2；必须携带 Idempotency-Key，docs/05 1.7） */
@Data
public class BookingBo {

    /** 关联日程ID（可选，会议与预约一一对应） */
    private String eventId;

    @NotBlank(message = "主题不能为空")
    @Size(max = 255, message = "主题长度不能超过{max}个字符")
    private String title;

    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    @NotBlank(message = "结束时间不能为空")
    private String endTime;
}
