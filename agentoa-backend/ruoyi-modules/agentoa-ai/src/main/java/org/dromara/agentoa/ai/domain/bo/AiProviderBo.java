package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型渠道写入（docs/21 AI-M1-01/02）。
 * <p>
 * {@code apiKey} 仅作为写入入参，回读只给 hint；两者都为空视为不修改密钥。
 */
@Data
public class AiProviderBo {

    private Long id;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 128, message = "渠道名称最长 128 字符")
    private String name;

    @NotBlank(message = "厂商类型不能为空")
    @Pattern(regexp = "openai|deepseek|qwen|moonshot|zhipu|gemini|anthropic|ollama|custom",
        message = "厂商类型不在字典范围内")
    private String providerType;

    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 255, message = "Base URL 最长 255 字符")
    private String baseUrl;

    /** 明文 Key（仅写入用，不落日志） */
    @Size(max = 512, message = "API Key 过长")
    private String apiKey;

    @Size(max = 128, message = "secret_ref 最长 128 字符")
    private String secretRef;

    private Integer priority = 100;

    private Integer enabled = 1;

    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
