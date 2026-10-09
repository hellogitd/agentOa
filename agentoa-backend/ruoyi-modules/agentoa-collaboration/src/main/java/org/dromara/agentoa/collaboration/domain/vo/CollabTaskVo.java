package org.dromara.agentoa.collaboration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 任务视图（Collab 前缀避免与 workflow 模块 TaskVo 别名冲突） */
@Data
public class CollabTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer lockVersion;

    private String title;

    private String description;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignerId;

    private String assignerName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneeId;

    private String assigneeName;

    private Integer priority;

    private Integer status;

    private String startDate;

    private String dueDate;

    private String completedTime;

    private Integer progress;

    private String tags;

    private List<Member> members = new ArrayList<>();

    private Boolean canManage;

    private String createTime;

    private String updateTime;

    @Data
    public static class Member implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long userId;

        private String nickname;
    }
}
