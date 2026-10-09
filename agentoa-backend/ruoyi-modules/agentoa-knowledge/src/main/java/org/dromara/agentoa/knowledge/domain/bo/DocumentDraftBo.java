package org.dromara.agentoa.knowledge.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人草稿保存载荷（docs/16：携带 baseVersion 用于并发冲突检测）。
 */
@Data
public class DocumentDraftBo {

    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    private String content;

    private Integer baseVersion;
}
