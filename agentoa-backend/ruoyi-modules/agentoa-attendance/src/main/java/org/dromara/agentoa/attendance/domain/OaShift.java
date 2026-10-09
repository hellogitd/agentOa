package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalTime;

/**
 * 班次 oa_shift，对应 API 资源 /api/v1/attendance/shifts。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_shift")
public class OaShift extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String shiftCode;

    private String shiftName;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    private LocalTime restStartTime;

    private LocalTime restEndTime;

    /** 是否跨夜班（0否 1是） */
    private Integer isCrossDay;

    /** 弹性分钟（晚到顺延下班） */
    private Integer flexibleMinutes;

    /** 迟到早退宽限分钟 */
    private Integer graceMinutes;

    /** 班次开始前允许打卡分钟 */
    private Integer punchWindowStart;

    /** 班次结束后允许打卡分钟 */
    private Integer punchWindowEnd;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}
