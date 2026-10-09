package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 假期额度 oa_leave_balance（分钟账本余额，唯一键 user + year + leaveType）。
 */
@Data
@TableName("oa_leave_balance")
public class OaLeaveBalance {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private Integer year;

    private String leaveType;

    private Integer totalMinutes;

    private Integer frozenMinutes;

    private Integer usedMinutes;

    private Integer lockVersion;

    private LocalDate expireDate;

    private Date createTime;

    private Date updateTime;
}
