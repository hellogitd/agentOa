package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 检索命中视图（docs/21 AI-M3-05/06）：命中片段 + 相似度 + 跳转链接。
 */
@Data
public class AiKbSearchHitVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long chunkId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;

    /** document/file */
    private String sourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long docId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String title;

    private String heading;

    private String snippet;

    /** 融合得分（0-1，越高越相关） */
    private Double score;

    /** 向量相似度（关键词命中无此项） */
    private Double vectorScore;

    /** 前端跳转链接（原文） */
    private String link;
}
