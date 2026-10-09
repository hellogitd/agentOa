package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class DefinitionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String processKey;

    private String processName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    private String categoryName;

    private String formKey;

    private String businessType;

    private Integer currentVersionNo;

    private String status;

    private String icon;

    private Integer sort;

    private String remark;

    /** 当前版本的表单设计 */
    private FormVo form;

    /** 当前版本的结构化审批链配置 JSON */
    private String chain;

    /** 是否来自代码仓库内置模板 */
    private Boolean builtin;

    private List<VersionVo> versions = new ArrayList<>();

    @Data
    public static class VersionVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Integer versionNo;

        private String bpmnResource;

        /** 是否为结构化审批链编译产物 */
        private Boolean compiled;

        private String validationSummary;

        private String status;

        private Date publishedTime;
    }
}
