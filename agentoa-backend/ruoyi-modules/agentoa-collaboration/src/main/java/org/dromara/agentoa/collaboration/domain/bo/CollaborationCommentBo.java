package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 任务评论入参（docs/05 9.3 /tasks/{id}/comments） */
@Data
public class CollaborationCommentBo {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论长度不能超过{max}个字符")
    private String content;

    /** 被@账号ID */
    private List<String> mentionIds;
}
