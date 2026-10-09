package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型写入（docs/21 AI-M1-04）。
 */
@Data
public class AiModelBo {

    private Long id;

    @NotNull(message = "所属渠道不能为空")
    private Long providerId;

    @NotBlank(message = "模型标识不能为空")
    @Size(max = 128, message = "模型标识最长 128 字符")
    private String modelKey;

    @Size(max = 128, message = "别名最长 128 字符")
    private String alias;

    /** 能力：chat/vision/embedding/rerank */
    private List<String> capability;

    @NotNull(message = "上下文窗口不能为空")
    private Integer contextWindow = 8192;

    @DecimalMin(value = "0.00", message = "温度最小 0")
    @DecimalMax(value = "2.00", message = "温度最大 2")
    private BigDecimal defaultTemperature = new BigDecimal("0.70");

    private Integer maxTokens = 2048;

    private Integer enabled = 1;

    private Integer isDefault = 0;

    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
