package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 提示词模板视图。
 */
@Data
public class AiPromptTemplateVo {

    private Long id;

    private String code;

    private String name;

    private String category;

    private String content;

    private Integer enabled;

    private Integer isBuiltin;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
