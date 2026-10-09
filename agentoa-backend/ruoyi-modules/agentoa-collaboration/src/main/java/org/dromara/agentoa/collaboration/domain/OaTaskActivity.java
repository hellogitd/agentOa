package org.dromara.agentoa.collaboration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 任务活动与评论（docs/04 9.5 / docs/17 第 5 步） */
@Data
@TableName("oa_task_activity")
public class OaTaskActivity {

    @TableId(value = "id")
    private Long id;

    private Long taskId;

    /** 类型（1创建 2状态变更 3进度 4评论 5成员变更） */
    private Integer activityType;

    private String content;

    private Long operatorId;

    /** 被@账号ID，逗号分隔 */
    private String mentionIds;

    private Date createTime;

    private Integer delFlag;
}
