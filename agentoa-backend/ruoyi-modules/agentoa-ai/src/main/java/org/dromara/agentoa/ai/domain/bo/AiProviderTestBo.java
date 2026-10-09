package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道测试连接入参（docs/21 AI-M1-03）。
 */
@Data
public class AiProviderTestBo {

    /** 测试 prompt，缺省用最小请求 */
    @Size(max = 500, message = "测试 prompt 最长 500 字符")
    private String prompt;

    /** 测试用模型 key，缺省取该渠道第一个启用模型 */
    @Size(max = 128, message = "模型标识过长")
    private String modelKey;
}
