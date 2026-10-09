package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 知识问答引用（docs/21 AI-M3-05）：文档名 + 章节/片段 + 跳转链接。
 */
@Data
public class QaCitationVo {

    /** 引用序号（1 起，与正文 [n] 对应） */
    private Integer index;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    /** document/file */
    private String sourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long docId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String title;

    private String heading;

    private String snippet;

    private Double score;

    /** 前端跳转链接 */
    private String link;
}
