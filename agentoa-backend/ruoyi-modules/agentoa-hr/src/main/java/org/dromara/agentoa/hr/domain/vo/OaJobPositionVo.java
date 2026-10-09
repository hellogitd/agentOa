package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaJobPosition;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务岗位视图对象（API 规范 3.2 资源 /api/v1/hr/posts）。
 */
@Data
@AutoMapper(target = OaJobPosition.class)
public class OaJobPositionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String positionCode;

    private String positionName;

    private String positionLevel;

    private Integer positionSort;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}
