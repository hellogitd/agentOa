package org.dromara.agentoa.hr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 业务岗位业务对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = OaJobPosition.class, reverseConvertGenerate = false)
public class OaJobPositionBo extends BaseEntity {

    private Long id;

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过{max}个字符")
    private String positionCode;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过{max}个字符")
    private String positionName;

    @Size(max = 32, message = "职级长度不能超过{max}个字符")
    private String positionLevel;

    private Integer positionSort;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}
