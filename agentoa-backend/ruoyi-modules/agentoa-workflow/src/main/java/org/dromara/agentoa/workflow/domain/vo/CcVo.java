package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 抄送视图（API 规范 4.4 GET /tasks/cc）。
 */
@Data
public class CcVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    private String title;

    private String businessType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long businessId;

    private Long senderUserId;

    private String senderName;

    private String comment;

    private Integer instanceStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date readTime;
}
