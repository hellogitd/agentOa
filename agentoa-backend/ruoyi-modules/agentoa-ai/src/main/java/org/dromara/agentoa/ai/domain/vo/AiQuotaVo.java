package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 配额视图。
 */
@Data
public class AiQuotaVo {

    private Long id;

    private String scopeType;

    private Long scopeId;

    private String scopeName;

    private String periodType;

    private Long tokenLimit;

    private Integer requestLimit;

    private Integer enabled;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
