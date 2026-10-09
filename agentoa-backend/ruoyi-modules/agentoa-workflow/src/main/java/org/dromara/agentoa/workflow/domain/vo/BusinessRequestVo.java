package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 业务申请单统一视图：字段差异收敛在 formData（JSON 字符串）中。
 */
@Data
public class BusinessRequestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String businessType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    private String title;

    /** 1草稿 2审批中 3已通过 4已拒绝 7已撤销（报销含 5待付款/6已付款） */
    private Integer status;

    private String formData;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private Date createTime;

    private Date updateTime;
}
