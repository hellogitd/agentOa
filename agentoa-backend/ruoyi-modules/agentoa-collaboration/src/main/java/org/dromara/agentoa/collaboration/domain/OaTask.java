package org.dromara.agentoa.collaboration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 任务（docs/04 9.4 / docs/17）：协作者在 oa_task_member，活动与评论在 oa_task_activity。 */
@Data
@TableName("oa_task")
public class OaTask {

    @TableId(value = "id")
    private Long id;

    private Integer lockVersion;

    private String title;

    private String description;

    private Long parentId;

    private Long assignerId;

    private Long assigneeId;

    /** 优先级（1P0 2P1 3P2 4P3） */
    private Integer priority;

    /** 状态（1待办 2进行中 3已阻塞 4已完成 5已取消） */
    private Integer status;

    private Date startDate;

    private Date dueDate;

    private Date completedTime;

    /** 进度（0-100） */
    private Integer progress;

    private String tags;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;
}
