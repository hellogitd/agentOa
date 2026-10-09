package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 渠道视图：永不携带 {@code apiKeyCipher}，仅回显脱敏 hint。
 */
@Data
public class AiProviderVo {

    private Long id;

    private String name;

    private String providerType;

    private String baseUrl;

    /** sk-***ab12 */
    private String apiKeyHint;

    /** 是否已配置 Key（密文或 secret_ref） */
    private Boolean hasApiKey;

    private String secretRef;

    private Integer priority;

    private Integer enabled;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
