package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiCopilotConfig;
import org.dromara.agentoa.ai.domain.OaAiPromptTemplate;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiPromptTemplateBo;
import org.dromara.agentoa.ai.domain.vo.AiPromptTemplateVo;
import org.dromara.agentoa.ai.mapper.OaAiCopilotConfigMapper;
import org.dromara.agentoa.ai.mapper.OaAiPromptTemplateMapper;
import org.dromara.agentoa.ai.service.IAiPromptTemplateService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示词模板实现：内置模板不可删除。
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements IAiPromptTemplateService {

    private final OaAiPromptTemplateMapper templateMapper;
    private final OaAiCopilotConfigMapper copilotConfigMapper;

    @Override
    public PageVo<AiPromptTemplateVo> page(AiPageQuery query) {
        IPage<OaAiPromptTemplate> result = templateMapper.selectPage(new Page<>(query.safePageNum(), query.safePageSize()),
            new LambdaQueryWrapper<OaAiPromptTemplate>().orderByAsc(OaAiPromptTemplate::getId));
        List<AiPromptTemplateVo> records = new ArrayList<>();
        for (OaAiPromptTemplate template : result.getRecords()) {
            records.add(toVo(template));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public List<AiPromptTemplateVo> listEnabled() {
        List<Long> sceneTemplates = new ArrayList<>();
        for (OaAiCopilotConfig config : copilotConfigMapper.selectList(new LambdaQueryWrapper<OaAiCopilotConfig>())) {
            if (config.getPromptTemplateId() != null) {
                sceneTemplates.add(config.getPromptTemplateId());
            }
        }
        List<OaAiPromptTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<OaAiPromptTemplate>()
            .eq(OaAiPromptTemplate::getEnabled, 1)
            .orderByAsc(OaAiPromptTemplate::getId));
        List<AiPromptTemplateVo> records = new ArrayList<>();
        for (OaAiPromptTemplate template : templates) {
            if (sceneTemplates.contains(template.getId())) {
                continue;
            }
            records.add(toVo(template));
        }
        return records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateVo create(AiPromptTemplateBo bo) {
        OaAiPromptTemplate template = new OaAiPromptTemplate();
        apply(template, bo);
        template.setIsBuiltin(0);
        try {
            templateMapper.insert(template);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("AI_PROMPT_CODE_EXISTS 模板编码已存在", 409);
        }
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPromptTemplateVo update(AiPromptTemplateBo bo) {
        OaAiPromptTemplate template = require(bo.getId());
        apply(template, bo);
        try {
            templateMapper.updateById(template);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("AI_PROMPT_CODE_EXISTS 模板编码已存在", 409);
        }
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OaAiPromptTemplate template = require(id);
        if (template.getIsBuiltin() != null && template.getIsBuiltin() == 1) {
            throw new ServiceException("AI_PROMPT_BUILTIN 内置模板不可删除", 409);
        }
        templateMapper.deleteById(id);
    }

    @Override
    public OaAiPromptTemplate require(Long id) {
        OaAiPromptTemplate template = id == null ? null : templateMapper.selectById(id);
        if (template == null) {
            throw new ServiceException("AI_PROMPT_NOT_FOUND 模板不存在", 404);
        }
        return template;
    }

    // ---------------------------------------------------------------- internals

    private void apply(OaAiPromptTemplate template, AiPromptTemplateBo bo) {
        template.setCode(bo.getCode().trim());
        template.setName(bo.getName().trim());
        template.setCategory(bo.getCategory());
        template.setContent(bo.getContent());
        template.setEnabled(bo.getEnabled() == null || bo.getEnabled() == 1 ? 1 : 0);
        template.setRemark(bo.getRemark());
    }

    private AiPromptTemplateVo toVo(OaAiPromptTemplate template) {
        AiPromptTemplateVo vo = new AiPromptTemplateVo();
        vo.setId(template.getId());
        vo.setCode(template.getCode());
        vo.setName(template.getName());
        vo.setCategory(template.getCategory());
        vo.setContent(template.getContent());
        vo.setEnabled(template.getEnabled());
        vo.setIsBuiltin(template.getIsBuiltin());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
