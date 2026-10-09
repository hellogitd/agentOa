package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档版本视图：版本内容仅在请求具体版本时返回。
 */
@Data
public class DocumentVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    private Integer version;

    private String title;

    /** 版本正文快照（仅版本详情返回） */
    private String content;

    private String changeSummary;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createBy;

    private String createByName;

    private String createTime;
}
