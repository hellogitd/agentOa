package org.dromara.agentoa.notice.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 模板发送入参（POST /api/v1/notice/templates/{code}/send）。
 */
@Data
public class TemplateSendBo {

    @NotNull(message = "受众范围不能为空")
    @Pattern(regexp = "1|2|3|4", message = "受众范围取值非法")
    private String scopeType;

    @Size(max = 2000, message = "范围值长度不能超过{max}个字符")
    private String scopeValues;

    /** 模板变量值（键必须在模板声明内） */
    private Map<String, String> vars;
}
