package org.dromara.agentoa.collaboration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 会议室预约视图：取消/释放保留历史 */
@Data
public class BookingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Integer lockVersion;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    private String roomName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long eventId;

    private String title;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookerId;

    private String bookerName;

    private String startTime;

    private String endTime;

    private String checkinTime;

    private Integer status;

    private Boolean canManage;

    private String createTime;
}
