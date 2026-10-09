package org.dromara.agentoa.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论命令（P1，KB-06）。
 */
@Data
public class CommentBo {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论长度不能超过{max}个字符")
    private String content;

    /** 父评论 ID（可空，回复时传） */
    private Long parentId;
}
