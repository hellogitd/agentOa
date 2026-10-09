package org.dromara.agentoa.collaboration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 会议室预约（docs/04 9.3）：与日程一一对应（event_id 唯一，可空支持独立预约），
 * 重叠由行锁拒绝而不是 Redis 锁。
 */
@Data
@TableName("oa_room_booking")
public class OaRoomBooking {

    @TableId(value = "id")
    private Long id;

    private Integer lockVersion;

    private Long roomId;

    private Long eventId;

    private String title;

    private Long bookerId;

    private Date startTime;

    private Date endTime;

    private Date checkinTime;

    /** 状态（1预订 2已签到 3已取消 4已释放） */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
