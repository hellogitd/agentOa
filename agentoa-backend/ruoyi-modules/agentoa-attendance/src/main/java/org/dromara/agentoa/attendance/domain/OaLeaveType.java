package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 假期类型与额度策略 oa_leave_type（编码对齐字典 at_leave_type）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_type")
public class OaLeaveType extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String typeCode;

    private String typeName;

    /** 是否计额度（1是 0否） */
    private Integer quotaLimited;

    /** 年度默认额度（分钟，0表示由 HR 发放） */
    private Integer defaultMinutes;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}
