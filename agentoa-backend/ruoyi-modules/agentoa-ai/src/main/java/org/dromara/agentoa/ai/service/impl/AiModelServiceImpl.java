package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.domain.bo.AiModelBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.enums.AiCapability;
import org.dromara.agentoa.ai.domain.vo.AiModelVo;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiProviderMapper;
import org.dromara.agentoa.ai.service.IAiModelService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型实现：全局默认模型唯一。
 */
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements IAiModelService {

    private final OaAiModelMapper modelMapper;
    private final OaAiProviderMapper providerMapper;

    @Override
    public PageVo<AiModelVo> page(AiModelBo query, AiPageQuery page) {
        LambdaQueryWrapper<OaAiModel> wrapper = new LambdaQueryWrapper<OaAiModel>()
            .eq(query != null && query.getProviderId() != null, OaAiModel::getProviderId, query == null ? null : query.getProviderId())
            .like(query != null && query.getModelKey() != null && !query.getModelKey().isBlank(),
                OaAiModel::getModelKey, query == null || query.getModelKey() == null ? null : query.getModelKey().trim())
            .orderByDesc(OaAiModel::getIsDefault)
            .orderByAsc(OaAiModel::getId);
        IPage<OaAiModel> result = modelMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        return PageVo.of(toVoList(result.getRecords()), result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiModelVo get(Long id) {
        return toVo(require(id));
    }

    @Override
    public List<AiModelVo> listEnabled() {
        List<OaAiModel> models = modelMapper.selectList(new LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getEnabled, 1)
            .orderByDesc(OaAiModel::getIsDefault)
            .orderByAsc(OaAiModel::getId));
        return toVoList(models);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelVo create(AiModelBo bo) {
        OaAiModel model = new OaAiModel();
        apply(model, bo);
        modelMapper.insert(model);
        if (model.getIsDefault() != null && model.getIsDefault() == 1) {
            clearOtherDefaults(model.getId());
        }
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelVo update(AiModelBo bo) {
        OaAiModel model = require(bo.getId());
        apply(model, bo);
        modelMapper.updateById(model);
        if (model.getIsDefault() != null && model.getIsDefault() == 1) {
            clearOtherDefaults(model.getId());
        }
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        modelMapper.deleteById(id);
    }

    // ---------------------------------------------------------------- internals

    private OaAiModel require(Long id) {
        OaAiModel model = id == null ? null : modelMapper.selectById(id);
        if (model == null) {
            throw new ServiceException("AI_MODEL_NOT_FOUND 模型不存在", 404);
        }
        return model;
    }

    private void apply(OaAiModel model, AiModelBo bo) {
        OaAiProvider provider = providerMapper.selectById(bo.getProviderId());
        if (provider == null) {
            throw new ServiceException("AI_PROVIDER_NOT_FOUND 渠道不存在", 404);
        }
        AiCapability.validate(bo.getCapability());
        model.setProviderId(bo.getProviderId());
        model.setModelKey(bo.getModelKey().trim());
        model.setAlias(bo.getAlias());
        model.setCapability(AiCapability.format(bo.getCapability()));
        model.setContextWindow(bo.getContextWindow() == null ? 8192 : bo.getContextWindow());
        model.setDefaultTemperature(bo.getDefaultTemperature());
        model.setMaxTokens(bo.getMaxTokens() == null ? 2048 : bo.getMaxTokens());
        model.setEnabled(bo.getEnabled() == null || bo.getEnabled() == 1 ? 1 : 0);
        model.setIsDefault(bo.getIsDefault() != null && bo.getIsDefault() == 1 ? 1 : 0);
        model.setRemark(bo.getRemark());
    }

    private void clearOtherDefaults(Long keepId) {
        List<OaAiModel> defaults = modelMapper.selectList(new LambdaQueryWrapper<OaAiModel>()
            .eq(OaAiModel::getIsDefault, 1));
        for (OaAiModel model : defaults) {
            if (!model.getId().equals(keepId)) {
                model.setIsDefault(0);
                modelMapper.updateById(model);
            }
        }
    }

    private List<AiModelVo> toVoList(List<OaAiModel> models) {
        if (models == null || models.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> providerIds = models.stream().map(OaAiModel::getProviderId).distinct().collect(Collectors.toList());
        Map<Long, OaAiProvider> providers = providerMapper.selectBatchIds(providerIds).stream()
            .collect(Collectors.toMap(OaAiProvider::getId, Function.identity()));
        List<AiModelVo> result = new ArrayList<>();
        for (OaAiModel model : models) {
            result.add(toVo(model, providers.get(model.getProviderId())));
        }
        return result;
    }

    private AiModelVo toVo(OaAiModel model) {
        return toVo(model, providerMapper.selectById(model.getProviderId()));
    }

    private AiModelVo toVo(OaAiModel model, OaAiProvider provider) {
        AiModelVo vo = new AiModelVo();
        vo.setId(model.getId());
        vo.setProviderId(model.getProviderId());
        vo.setProviderName(provider == null ? null : provider.getName());
        vo.setModelKey(model.getModelKey());
        vo.setAlias(model.getAlias());
        vo.setCapability(AiCapability.parse(model.getCapability()));
        vo.setContextWindow(model.getContextWindow());
        vo.setDefaultTemperature(model.getDefaultTemperature());
        vo.setMaxTokens(model.getMaxTokens());
        vo.setEnabled(model.getEnabled());
        vo.setIsDefault(model.getIsDefault());
        vo.setRemark(model.getRemark());
        vo.setCreateTime(model.getCreateTime());
        vo.setUpdateTime(model.getUpdateTime());
        return vo;
    }
}
