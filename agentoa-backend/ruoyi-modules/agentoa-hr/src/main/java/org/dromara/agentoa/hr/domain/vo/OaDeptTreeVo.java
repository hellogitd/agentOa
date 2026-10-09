package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树视图对象（API 规范 3.1）：{id, name, parentId, sort, status, children}。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OaDeptTreeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private Integer sort;

    /** 0正常 1停用 */
    private String status;

    private List<OaDeptTreeVo> children = new ArrayList<>();
}
