package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工异动历史 oa_employee_change（P1，API 规范 3.6）。
 * <p>
 * 异动按 effective_date 生效：到期后写入 oa_employee_history 并更新档案；
 * source_request_id 唯一键保证一次来源申请只产生一个生效事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_change")
public class OaEmployeeChange extends TenantEntity {

    /** 调岗 */
    public static final int TYPE_TRANSFER = 3;
    /** 调薪 */
    public static final int TYPE_SALARY = 4;
    /** 晋升 */
    public static final int TYPE_PROMOTION = 5;
    /** 降级 */
    public static final int TYPE_DEMOTION = 6;

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    /** 异动类型（3调岗 4调薪 5晋升 6降级） */
    private Integer changeType;

    private LocalDate effectiveDate;

    private Long oldDeptId;

    private Long newDeptId;

    private Long oldPostId;

    private Long newPostId;

    private String oldPositionLevel;

    private String newPositionLevel;

    /** 原薪资密文 */
    private String oldSalary;

    /** 新薪资密文 */
    private String newSalary;

    private String salaryKeyVersion;

    private String reason;

    /** 是否已生效（1是 0否） */
    private Integer applied;

    /** 生效事件 ID（幂等） */
    private String eventId;

    /** 关联流程实例 ID */
    private String flowInstanceId;

    /** 来源申请 ID（一次生效去重） */
    private Long sourceRequestId;
}
