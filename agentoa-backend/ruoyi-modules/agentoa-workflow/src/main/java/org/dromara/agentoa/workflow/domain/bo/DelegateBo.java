package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.agentoa.workflow.domain.OaFlowDelegate;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 委托代理业务对象（API 规范 4.5）。ownerId 服务端取当前登录用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DelegateBo extends BaseEntity {

    private Long id;

    @NotNull(message = "受托人不能为空")
    private Long delegateId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 适用流程（逗号分隔，空=全部） */
    @Size(max = 255, message = "适用流程长度不能超过{max}个字符")
    private String processKeys;

    /** 状态（1启用 0停用） */
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

    /** 查询条件：委托人（管理员查他人用） */
    private Long ownerId;

    public OaFlowDelegate toEntity() {
        OaFlowDelegate entity = new OaFlowDelegate();
        entity.setId(id);
        entity.setDelegateId(delegateId);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setProcessKeys(processKeys);
        entity.setStatus(status == null ? 1 : status);
        entity.setRemark(remark);
        return entity;
    }
}
