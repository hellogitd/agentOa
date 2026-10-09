package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 委托代理视图（API 规范 4.5）。
 */
@Data
public class DelegateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private String ownerName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long delegateId;

    private String delegateName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String processKeys;

    /** 状态（1启用 0停用） */
    private Integer status;

    private String remark;
}
