package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型路由（docs/21 AI-M1-05）：按显式模型或默认模型选择渠道；
 * 同模型多渠道按优先级 + 健康度排序，失败自动切换备用渠道（最多 2 次）。
 */
@Component
@RequiredArgsConstructor
public class AiModelRouter {

    /** 初始 + 备用渠道最多 2 次 */
    public static final int MAX_CANDIDATES = 3;

    private final OaAiModelMapper modelMapper;
    private final ProviderRegistry registry;
    private final ProviderHealth health;

    /** 一路候选：模型 + 渠道 + 已解析密钥 */
    public record Route(OaAiModel model, OaAiProvider provider, String apiKey) {

        public String modelKey() {
            return model.getModelKey();
        }
    }

    /**
     * 候选路由（已排序、已截断）。
     *
     * @param modelId 显式模型，空则取全局默认模型
     */
    public List<Route> candidates(Long modelId) {
        OaAiModel anchor = resolveAnchor(modelId);
        List<OaAiModel> sameModel = modelMapper.selectList(new LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getModelKey, anchor.getModelKey())
            .eq(OaAiModel::getEnabled, 1));
        Map<Long, OaAiProvider> providers = new LinkedHashMap<>();
        for (OaAiModel model : sameModel) {
            OaAiProvider provider = registry.requireProvider(model.getProviderId());
            if (provider.getEnabled() != null && provider.getEnabled() == 1) {
                providers.putIfAbsent(provider.getId(), provider);
            }
        }
        if (providers.isEmpty()) {
            throw new ServiceException("AI_PROVIDER_UNAVAILABLE 没有可用渠道", 400);
        }
        List<Route> routes = new ArrayList<>();
        for (OaAiModel model : sameModel) {
            OaAiProvider provider = providers.get(model.getProviderId());
            if (provider == null) {
                continue;
            }
            String apiKey = registry.resolveApiKey(provider);
            if (apiKey == null || apiKey.isBlank()) {
                continue;
            }
            routes.add(new Route(model, provider, apiKey));
        }
        if (routes.isEmpty()) {
            throw new ServiceException("AI_PROVIDER_KEY_MISSING 渠道未配置可用密钥", 400);
        }
        routes.sort(Comparator
            .comparingInt((Route route) -> route.provider().getPriority() == null ? Integer.MAX_VALUE : route.provider().getPriority())
            .thenComparing(route -> health.isHealthy(route.provider().getId()) ? 0 : 1));
        return routes.subList(0, Math.min(routes.size(), MAX_CANDIDATES));
    }

    private OaAiModel resolveAnchor(Long modelId) {
        if (modelId != null) {
            OaAiModel model = registry.requireModel(modelId);
            if (model.getEnabled() == null || model.getEnabled() != 1) {
                throw new ServiceException("AI_MODEL_DISABLED 模型已停用", 400);
            }
            return model;
        }
        OaAiModel defaultModel = modelMapper.selectOne(new LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getIsDefault, 1)
            .eq(OaAiModel::getEnabled, 1)
            .last("LIMIT 1"));
        if (defaultModel == null) {
            throw new ServiceException("AI_MODEL_UNAVAILABLE 未配置默认模型", 400);
        }
        return defaultModel;
    }
}
