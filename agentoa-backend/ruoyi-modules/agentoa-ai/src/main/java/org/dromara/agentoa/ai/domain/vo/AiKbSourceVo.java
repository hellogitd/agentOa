package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 知识域数据源视图（docs/21 AI-M3-02）。
 */
@Data
public class AiKbSourceVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;

    /** document/file */
    private String sourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long docId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String title;

    private Integer chunkCount;

    /** pending/indexing/ready/failed */
    private String indexStatus;

    private String errorMsg;

    private Date indexedAt;

    private Date createTime;

    private Date updateTime;
}
