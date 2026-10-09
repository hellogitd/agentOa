package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 检索测试请求（docs/21 AI-M3-06）。
 */
@Data
public class AiKbSearchTestBo {

    @NotBlank(message = "检索问题不能为空")
    @Size(max = 2000, message = "检索问题最长{max}个字符")
    private String query;

    /** 返回条数（默认 5） */
    private Integer topK;
}
