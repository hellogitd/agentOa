package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployeeChange;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 员工异动视图对象（API 规范 3.6）。薪资明文仅对持 hr:change:sensitive 的调用方解密，其余返回脱敏值。
 */
@Data
@AutoMapper(target = OaEmployeeChange.class)
public class OaChangeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    /** 员工姓名，服务层填充 */
    private String employeeName;

    /** 异动类型（3调岗 4调薪 5晋升 6降级） */
    private Integer changeType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long oldDeptId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long newDeptId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long oldPostId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long newPostId;

    private String oldPositionLevel;

    private String newPositionLevel;

    /** 原薪资（脱敏或明文，服务层填充） */
    private String oldSalary;

    /** 新薪资（脱敏或明文，服务层填充） */
    private String newSalary;

    private String reason;

    /** 是否已生效（1是 0否） */
    private Integer applied;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceRequestId;

    private String flowInstanceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date createTime;

    private String createByName;
}
