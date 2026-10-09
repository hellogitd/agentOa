package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人草稿视图（携带 baseVersion）。
 */
@Data
public class DocumentDraftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Integer baseVersion;

    private String title;

    private String content;

    private String updateTime;
}
