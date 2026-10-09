package org.dromara.agentoa.collaboration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 会议室视图 */
@Data
public class RoomVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String location;

    private Integer capacity;

    private String equipment;

    private Integer status;

    private String createTime;

    private String updateTime;
}
