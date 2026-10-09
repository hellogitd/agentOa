package org.dromara.agentoa.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识空间新建/修改载荷（docs/16 8.1）。
 */
@Data
public class SpaceBo {

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 128, message = "空间名称长度不能超过{max}个字符")
    private String name;

    @Size(max = 64, message = "空间图标长度不能超过{max}个字符")
    private String icon;

    @Size(max = 500, message = "空间简介长度不能超过{max}个字符")
    private String description;

    @NotNull(message = "空间类型不能为空")
    @Pattern(regexp = "1|2|3", message = "空间类型取值非法")
    private String spaceType;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
