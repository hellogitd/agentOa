package org.dromara.agentoa.notice.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通知偏好更新入参（仅抑制实时提醒，消息仍持久化可补拉）。
 */
@Data
public class PreferenceBo {

    @NotBlank(message = "消息类型不能为空")
    @Size(max = 32, message = "消息类型长度不能超过{max}个字符")
    private String msgType;

    @NotNull(message = "是否提醒不能为空")
    @Pattern(regexp = "0|1", message = "是否提醒取值非法")
    private String enabled;

    /** 批量更新 */
    private List<PreferenceBo> items;
}
