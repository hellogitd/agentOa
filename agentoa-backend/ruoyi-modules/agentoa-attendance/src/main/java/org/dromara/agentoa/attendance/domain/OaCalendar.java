package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * 组织工作日历 oa_calendar：日期类型优先于班次工作日配置。
 */
@Data
@TableName("oa_calendar")
public class OaCalendar {

    @TableId(value = "id")
    private Long id;

    private LocalDate workDate;

    /** 0工作日 1节假日 2调休上班 */
    private String dayType;

    private String description;

    private Integer ruleVersion;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;
}
