package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 模型渠道 oa_ai_provider（docs/21 AI-M1-01）。
 * <p>
 * API Key 仅存密文（{@code api_key_cipher}）与脱敏提示（{@code api_key_hint}），
 * 明文永不回显、永不落日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_provider")
public class OaAiProvider extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String name;

    /** 厂商类型（ai_provider_type 字典） */
    private String providerType;

    /** OpenAI 兼容 Base URL，仅允许配置值出站 */
    private String baseUrl;

    /** AES-256-GCM 密文 keyVersion:base64(nonce||tag||ct) */
    private String apiKeyCipher;

    /** 脱敏提示（sk-***ab12） */
    private String apiKeyHint;

    /** 密钥引用（环境变量/configtree 名），优先于密文 */
    private String secretRef;

    /** 优先级，小值优先 */
    private Integer priority;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private String remark;
}
