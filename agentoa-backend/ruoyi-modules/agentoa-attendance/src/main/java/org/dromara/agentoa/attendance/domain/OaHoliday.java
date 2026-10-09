package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 节假日登记 oa_holiday（HOLIDAY 节假日 / MAKEUP 调休上班）。
 */
@Data
@TableName("oa_holiday")
public class OaHoliday {

    @TableId(value = "id")
    private Long id;

    private LocalDate holidayDate;

    private String holidayName;

    private String holidayType;

    private Integer year;

    private Long createBy;

    private Date createTime;
}
