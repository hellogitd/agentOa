package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaContract;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 合同视图对象（需求 HR-09）。
 */
@Data
@AutoMapper(target = OaContract.class)
public class OaContractVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    private String employeeName;

    private String employeeNo;

    private String contractNo;

    private String contractType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate signDate;

    private Integer renewCount;

    /** 状态（1生效 2到期 3终止） */
    private Integer status;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    /** 距到期天数（已到期为负；服务层计算，续签提醒用） */
    private Long daysToExpire;

    private String remark;
}
