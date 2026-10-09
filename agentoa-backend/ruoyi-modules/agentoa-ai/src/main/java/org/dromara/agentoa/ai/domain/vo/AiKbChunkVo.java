package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 知识分块视图（docs/21 AI-M3-06 分块预览）。
 */
@Data
public class AiKbChunkVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    private Integer seq;

    private String heading;

    private String content;

    private Integer tokenCount;
}
