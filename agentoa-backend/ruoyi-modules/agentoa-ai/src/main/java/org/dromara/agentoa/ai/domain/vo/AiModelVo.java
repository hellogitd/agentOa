package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 模型视图。
 */
@Data
public class AiModelVo {

    private Long id;

    private Long providerId;

    private String providerName;

    private String modelKey;

    private String alias;

    private List<String> capability;

    private Integer contextWindow;

    private BigDecimal defaultTemperature;

    private Integer maxTokens;

    private Integer enabled;

    private Integer isDefault;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
