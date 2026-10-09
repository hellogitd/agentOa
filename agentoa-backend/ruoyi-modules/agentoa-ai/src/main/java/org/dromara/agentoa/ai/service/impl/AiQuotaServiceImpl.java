package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiQuota;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQuotaBo;
import org.dromara.agentoa.ai.domain.vo.AiQuotaVo;
import org.dromara.agentoa.ai.mapper.OaAiQuotaMapper;
import org.dromara.agentoa.ai.service.IAiQuotaService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 用量配额实现：（scope_type, scope_id, period_type）唯一。
 */
@Service
@RequiredArgsConstructor
public class AiQuotaServiceImpl implements IAiQuotaService {

    private final OaAiQuotaMapper quotaMapper;

    @Override
    public PageVo<AiQuotaVo> page(AiPageQuery query) {
        IPage<OaAiQuota> result = quotaMapper.selectPage(new Page<>(query.safePageNum(), query.safePageSize()),
            new LambdaQueryWrapper<OaAiQuota>().orderByAsc(OaAiQuota::getScopeType).orderByAsc(OaAiQuota::getId));
        List<AiQuotaVo> records = new ArrayList<>();
        for (OaAiQuota quota : result.getRecords()) {
            records.add(toVo(quota));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiQuotaVo create(AiQuotaBo bo) {
        OaAiQuota quota = new OaAiQuota();
        apply(quota, bo);
        try {
            quotaMapper.insert(quota);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("AI_QUOTA_EXISTS 该主体与周期已有配额", 409);
        }
        return toVo(quota);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiQuotaVo update(AiQuotaBo bo) {
        OaAiQuota quota = quotaMapper.selectById(bo.getId());
        if (quota == null) {
            throw new ServiceException("AI_QUOTA_NOT_FOUND 配额不存在", 404);
        }
        apply(quota, bo);
        try {
            quotaMapper.updateById(quota);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("AI_QUOTA_EXISTS 该主体与周期已有配额", 409);
        }
        return toVo(quota);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (quotaMapper.selectById(id) == null) {
            throw new ServiceException("AI_QUOTA_NOT_FOUND 配额不存在", 404);
        }
        quotaMapper.deleteById(id);
    }

    // ---------------------------------------------------------------- internals

    private void apply(OaAiQuota quota, AiQuotaBo bo) {
        quota.setScopeType(bo.getScopeType());
        quota.setScopeId(bo.getScopeId());
        quota.setScopeName(bo.getScopeName());
        quota.setPeriodType(bo.getPeriodType());
        quota.setTokenLimit(bo.getTokenLimit());
        quota.setRequestLimit(bo.getRequestLimit());
        quota.setEnabled(bo.getEnabled() == null || bo.getEnabled() == 1 ? 1 : 0);
        quota.setRemark(bo.getRemark());
    }

    private AiQuotaVo toVo(OaAiQuota quota) {
        AiQuotaVo vo = new AiQuotaVo();
        vo.setId(quota.getId());
        vo.setScopeType(quota.getScopeType());
        vo.setScopeId(quota.getScopeId());
        vo.setScopeName(quota.getScopeName());
        vo.setPeriodType(quota.getPeriodType());
        vo.setTokenLimit(quota.getTokenLimit());
        vo.setRequestLimit(quota.getRequestLimit());
        vo.setEnabled(quota.getEnabled());
        vo.setRemark(quota.getRemark());
        vo.setCreateTime(quota.getCreateTime());
        vo.setUpdateTime(quota.getUpdateTime());
        return vo;
    }
}
