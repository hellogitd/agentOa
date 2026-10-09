package org.dromara.agentoa.collaboration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 会议室（docs/04 9.2） */
@Data
@TableName("oa_meeting_room")
public class OaMeetingRoom {

    @TableId(value = "id")
    private Long id;

    private String name;

    private String location;

    private Integer capacity;

    /** 设备（投影/视频/白板） */
    private String equipment;

    /** 状态（1可用 2维护中） */
    private Integer status;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private Integer delFlag;
}
