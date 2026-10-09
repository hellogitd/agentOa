package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 知识域视图（docs/21 AI-M3-01）。
 */
@Data
public class AiKbVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    /** private/members/all */
    private String visibility;

    /** 成员账号 ID 列表 */
    private List<Long> memberUserIds;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long embeddingModelId;

    /** 向量模型显示名（富余展示） */
    private String embeddingModelName;

    /** active/disabled */
    private String status;

    /** 是否创建者（对象权限提示） */
    private Boolean owner;

    private Integer sourceCount;

    /** 分块总量（docs/21 §9.2 向量库切换阈值度量） */
    private Integer chunkCount;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
