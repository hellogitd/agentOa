package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 加班核定记录 oa_overtime：审批通过的加班申请生成，供月报统计。
 */
@Data
@TableName("oa_overtime")
public class OaOvertime {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private Long overtimeRequestId;

    private LocalDate overtimeDate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMinutes;

    /** weekday/weekend/holiday */
    private String overtimeType;

    /** 3已通过 7已撤销 */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
