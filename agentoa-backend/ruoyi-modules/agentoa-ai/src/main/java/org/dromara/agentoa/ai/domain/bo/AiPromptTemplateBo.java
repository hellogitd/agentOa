package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提示词模板写入（docs/21 AI-M2-05）。
 */
@Data
public class AiPromptTemplateBo {

    private Long id;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码最长 64 字符")
    private String code;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称最长 128 字符")
    private String name;

    @Size(max = 64, message = "分类最长 64 字符")
    private String category;

    @NotBlank(message = "模板内容不能为空")
    @Size(max = 8000, message = "模板内容最长 8000 字符")
    private String content;

    private Integer enabled = 1;

    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
