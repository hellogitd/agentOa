package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 知识域维护请求（docs/21 AI-M3-01）。
 */
@Data
public class AiKbBo {

    private Long id;

    @NotBlank(message = "知识域名称不能为空")
    @Size(max = 128, message = "知识域名称长度不能超过{max}个字符")
    private String name;

    @Size(max = 500, message = "描述长度不能超过{max}个字符")
    private String description;

    /** private/members/all，默认 private */
    private String visibility = "private";

    /** 成员账号 ID 列表 */
    @Size(max = 200, message = "成员最多{max}人")
    private List<Long> memberUserIds;

    /** 向量模型 ID（capability 需含 embedding） */
    private Long embeddingModelId;

    /** active/disabled */
    private String status = "active";

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
