package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 搜索命中视图（docs/05 8.3 响应结构）。
 */
@Data
public class SearchHitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    private String title;

    /** 命中上下文（<em>高亮</em>） */
    private String highlight;

    private String spaceName;

    private String lastEditTime;
}
