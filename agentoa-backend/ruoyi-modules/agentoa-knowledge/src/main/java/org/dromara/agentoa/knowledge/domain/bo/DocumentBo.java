package org.dromara.agentoa.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文档新建/版本提交载荷（docs/16：客户端携带 baseVersion，冲突返回 409）。
 */
@Data
public class DocumentBo {

    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    private Long parentId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    private String content;

    @Size(max = 255, message = "标签长度不能超过{max}个字符")
    private String tags;

    @Pattern(regexp = "markdown|rich", message = "文档类型取值非法")
    private String docType;

    /** 版本提交基线：更新必填，新增忽略 */
    private Integer baseVersion;

    @Size(max = 255, message = "变更摘要长度不能超过{max}个字符")
    private String changeSummary;
}
