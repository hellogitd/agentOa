package org.dromara.agentoa.attendance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaLeaveLedger;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 额度账本流水视图。
 */
@Data
@AutoMapper(target = OaLeaveLedger.class)
public class LedgerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long balanceId;

    private String eventKey;

    private String businessType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long businessId;

    private Integer submissionNo;

    private String action;

    private Integer totalDelta;

    private Integer frozenDelta;

    private Integer usedDelta;

    private Integer leaveMinutes;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private String operatorName;

    private Date createTime;
}
