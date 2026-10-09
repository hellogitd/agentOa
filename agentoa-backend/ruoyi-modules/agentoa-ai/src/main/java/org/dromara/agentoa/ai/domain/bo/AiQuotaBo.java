package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 配额写入（docs/21 AI-M1-08）。
 */
@Data
public class AiQuotaBo {

    private Long id;

    @NotBlank(message = "主体类型不能为空")
    @Pattern(regexp = "user|role", message = "主体类型仅支持 user/role")
    private String scopeType;

    @NotNull(message = "主体 ID 不能为空")
    private Long scopeId;

    @Size(max = 128, message = "主体名称最长 128 字符")
    private String scopeName;

    @NotBlank(message = "周期类型不能为空")
    @Pattern(regexp = "day|month", message = "周期类型仅支持 day/month")
    private String periodType;

    /** token 限额，空表示不限 */
    private Long tokenLimit;

    /** 请求次数限额，空表示不限 */
    private Integer requestLimit;

    private Integer enabled = 1;

    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
