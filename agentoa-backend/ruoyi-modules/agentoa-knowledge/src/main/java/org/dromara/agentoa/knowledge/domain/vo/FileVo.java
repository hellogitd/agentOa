package org.dromara.agentoa.knowledge.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件柜视图。
 */
@Data
public class FileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long spaceId;

    private String spaceName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    private String documentTitle;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String fileName;

    private String fileExt;

    private Long fileSize;

    private String contentType;

    private Integer downloadCount;

    /** 回收站起算时间（空为未删除） */
    private String deletedAt;

    /** 当前用户有效角色 */
    private String myRole;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long createBy;

    private String createByName;

    private String createTime;
}
