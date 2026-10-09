package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployeeHistory;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 员工状态变更历史视图对象。
 */
@Data
@AutoMapper(target = OaEmployeeHistory.class)
public class OaEmployeeHistoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long employeeId;

    private String eventId;

    private String eventType;

    private String fromStatus;

    private String toStatus;

    private String detail;

    private String workflowInstanceId;

    private Long operatorUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date operateTime;

    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorName;
}
