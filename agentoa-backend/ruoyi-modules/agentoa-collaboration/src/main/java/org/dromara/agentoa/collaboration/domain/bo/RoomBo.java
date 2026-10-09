package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 会议室入参（docs/05 9.2） */
@Data
public class RoomBo {

    @NotBlank(message = "会议室名称不能为空")
    @Size(max = 64, message = "名称长度不能超过{max}个字符")
    private String name;

    @Size(max = 255, message = "位置长度不能超过{max}个字符")
    private String location;

    private Integer capacity;

    @Size(max = 255, message = "设备描述长度不能超过{max}个字符")
    private String equipment;

    /** 状态（1可用 2维护中） */
    private String status;
}
