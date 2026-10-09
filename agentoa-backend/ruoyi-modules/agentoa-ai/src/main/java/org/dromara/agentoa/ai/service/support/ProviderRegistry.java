package org.dromara.agentoa.ai.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiProviderMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 渠道配置注册表（docs/21 §2.2）：Key 双通道解析。
 * <p>
 * 解析顺序：{@code secret_ref}（环境变量 → system property → configtree 属性）
 * → 库中 {@code api_key_cipher} 解密 → 无则渠道不可用。
 */
@Component
@RequiredArgsConstructor
public class ProviderRegistry {

    private final OaAiProviderMapper providerMapper;
    private final OaAiModelMapper modelMapper;
    private final Environment environment;

    /** 解析后的渠道（apiKey 永不出现在响应与日志） */
    public record ResolvedProvider(OaAiProvider provider, String apiKey) {
    }

    public OaAiProvider requireProvider(Long providerId) {
        OaAiProvider provider = providerId == null ? null : providerMapper.selectById(providerId);
        if (provider == null) {
            throw new ServiceException("AI_PROVIDER_NOT_FOUND 渠道不存在", 404);
        }
        return provider;
    }

    public OaAiModel requireModel(Long modelId) {
        OaAiModel model = modelId == null ? null : modelMapper.selectById(modelId);
        if (model == null) {
            throw new ServiceException("AI_MODEL_NOT_FOUND 模型不存在", 404);
        }
        return model;
    }

    /** 渠道下启用模型（按 ID 升序） */
    public List<OaAiModel> modelsOf(Long providerId) {
        return modelMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getProviderId, providerId)
            .eq(OaAiModel::getEnabled, 1)
            .orderByAsc(OaAiModel::getId));
    }

    /** 解析 API Key，无可用密钥返回 null */
    public String resolveApiKey(OaAiProvider provider) {
        String secretRef = provider.getSecretRef();
        if (secretRef != null && !secretRef.isBlank()) {
            String fromEnv = System.getenv(secretRef);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
            String fromProperty = System.getProperty(secretRef);
            if (fromProperty != null && !fromProperty.isBlank()) {
                return fromProperty.trim();
            }
            if (environment != null) {
                String fromConfig = environment.getProperty(secretRef);
                if (fromConfig != null && !fromConfig.isBlank()) {
                    return fromConfig.trim();
                }
            }
        }
        if (provider.getApiKeyCipher() != null && !provider.getApiKeyCipher().isBlank()) {
            return AiKeyCipher.decrypt(provider.getApiKeyCipher());
        }
        return null;
    }

    /** 解析渠道并校验密钥可用 */
    public ResolvedProvider resolve(Long providerId) {
        OaAiProvider provider = requireProvider(providerId);
        if (provider.getEnabled() == null || provider.getEnabled() != 1) {
            throw new ServiceException("AI_PROVIDER_DISABLED 渠道已停用", 400);
        }
        String apiKey = resolveApiKey(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException("AI_PROVIDER_KEY_MISSING 渠道未配置可用密钥", 400);
        }
        return new ResolvedProvider(provider, apiKey);
    }
}
