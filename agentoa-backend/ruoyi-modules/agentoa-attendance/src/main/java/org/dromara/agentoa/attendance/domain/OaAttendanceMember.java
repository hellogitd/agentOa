package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 考勤组成员生效区间 oa_attendance_member。
 */
@Data
@TableName("oa_attendance_member")
public class OaAttendanceMember {

    @TableId(value = "id")
    private Long id;

    private Long groupId;

    private Long userId;

    private Long employeeId;

    private LocalDate validFrom;

    /** 空表示长期 */
    private LocalDate validTo;

    private Long createBy;

    private Date createTime;
}
