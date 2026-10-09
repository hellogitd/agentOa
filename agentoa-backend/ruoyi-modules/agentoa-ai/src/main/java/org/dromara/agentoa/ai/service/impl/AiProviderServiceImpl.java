package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiProviderBo;
import org.dromara.agentoa.ai.domain.bo.AiProviderTestBo;
import org.dromara.agentoa.ai.domain.vo.AiProviderTestVo;
import org.dromara.agentoa.ai.domain.vo.AiProviderVo;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiProviderMapper;
import org.dromara.agentoa.ai.service.IAiProviderService;
import org.dromara.agentoa.ai.service.support.AiKeyCipher;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型渠道实现：Key 只进不出（写入加密、回读仅 hint）。
 */
@Service
@RequiredArgsConstructor
public class AiProviderServiceImpl implements IAiProviderService {

    private final OaAiProviderMapper providerMapper;
    private final OaAiModelMapper modelMapper;
    private final LlmGateway llmGateway;

    @Override
    public PageVo<AiProviderVo> page(AiPageQuery query) {
        LambdaQueryWrapper<OaAiProvider> wrapper = new LambdaQueryWrapper<OaAiProvider>()
            .orderByAsc(OaAiProvider::getPriority)
            .orderByDesc(OaAiProvider::getCreateTime);
        IPage<OaAiProvider> result = providerMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<AiProviderVo> records = new ArrayList<>();
        for (OaAiProvider provider : result.getRecords()) {
            records.add(toVo(provider));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public AiProviderVo get(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProviderVo create(AiProviderBo bo) {
        OaAiProvider provider = new OaAiProvider();
        apply(provider, bo);
        providerMapper.insert(provider);
        return toVo(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProviderVo update(AiProviderBo bo) {
        OaAiProvider provider = require(bo.getId());
        apply(provider, bo);
        providerMapper.updateById(provider);
        return toVo(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long referenced = modelMapper.selectCount(new LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getProviderId, id));
        if (referenced != null && referenced > 0) {
            throw new ServiceException("AI_PROVIDER_IN_USE 渠道下仍有模型，禁止删除", 409);
        }
        providerMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer enabled) {
        OaAiProvider provider = require(id);
        provider.setEnabled(enabled == null || enabled == 1 ? 1 : 0);
        providerMapper.updateById(provider);
    }

    @Override
    public AiProviderTestVo test(Long id, AiProviderTestBo bo) {
        require(id);
        AiProviderTestBo safe = bo == null ? new AiProviderTestBo() : bo;
        return llmGateway.probe(id, safe.getModelKey(), safe.getPrompt());
    }

    // ---------------------------------------------------------------- internals

    private OaAiProvider require(Long id) {
        OaAiProvider provider = id == null ? null : providerMapper.selectById(id);
        if (provider == null) {
            throw new ServiceException("AI_PROVIDER_NOT_FOUND 渠道不存在", 404);
        }
        return provider;
    }

    private void apply(OaAiProvider provider, AiProviderBo bo) {
        provider.setName(bo.getName().trim());
        provider.setProviderType(bo.getProviderType());
        provider.setBaseUrl(normalizeBaseUrl(bo.getBaseUrl()));
        provider.setSecretRef(blankToNull(bo.getSecretRef()));
        provider.setPriority(bo.getPriority() == null ? 100 : bo.getPriority());
        provider.setEnabled(bo.getEnabled() == null || bo.getEnabled() == 1 ? 1 : 0);
        provider.setRemark(bo.getRemark());
        String apiKey = bo.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            String plaintext = apiKey.trim();
            provider.setApiKeyCipher(AiKeyCipher.encrypt(plaintext));
            provider.setApiKeyHint(AiKeyCipher.hint(plaintext));
        }
    }

    /** 出站地址只允许配置值：要求 http(s) 且不带用户输入拼接 */
    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new ServiceException("AI_PROVIDER_URL_INVALID Base URL 必须是 http(s) 地址", 400);
        }
        if (url.length() > 255) {
            throw new ServiceException("AI_PROVIDER_URL_INVALID Base URL 过长", 400);
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AiProviderVo toVo(OaAiProvider provider) {
        AiProviderVo vo = new AiProviderVo();
        vo.setId(provider.getId());
        vo.setName(provider.getName());
        vo.setProviderType(provider.getProviderType());
        vo.setBaseUrl(provider.getBaseUrl());
        vo.setApiKeyHint(provider.getApiKeyHint());
        vo.setHasApiKey((provider.getApiKeyCipher() != null && !provider.getApiKeyCipher().isBlank())
            || (provider.getSecretRef() != null && !provider.getSecretRef().isBlank()));
        vo.setSecretRef(provider.getSecretRef());
        vo.setPriority(provider.getPriority());
        vo.setEnabled(provider.getEnabled());
        vo.setRemark(provider.getRemark());
        vo.setCreateTime(provider.getCreateTime());
        vo.setUpdateTime(provider.getUpdateTime());
        return vo;
    }
}
