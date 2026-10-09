package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryBo {

    @NotBlank(message = "分类编码不能为空")
    @Size(max = 32, message = "分类编码长度不能超过{max}个字符")
    @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "分类编码只能包含字母、数字、下划线和短横线")
    private String code;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称长度不能超过{max}个字符")
    private String name;

    private Integer sort;

    @Pattern(regexp = "0|1", message = "状态取值非法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
