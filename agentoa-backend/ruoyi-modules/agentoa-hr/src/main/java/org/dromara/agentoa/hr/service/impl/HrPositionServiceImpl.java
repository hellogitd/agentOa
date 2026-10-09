package org.dromara.agentoa.hr.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaJobPositionBo;
import org.dromara.agentoa.hr.domain.vo.OaJobPositionVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaJobPositionMapper;
import org.dromara.agentoa.hr.service.IHrPositionService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务岗位服务实现。
 */
@RequiredArgsConstructor
@Service
public class HrPositionServiceImpl implements IHrPositionService {

    private final OaJobPositionMapper baseMapper;
    private final OaEmployeeMapper employeeMapper;

    @Override
    public PageVo<OaJobPositionVo> selectPagePositions(OaJobPositionBo query, HrPageQuery page) {
        Page<OaJobPosition> mpPage = new Page<>(page.safePageNum(), page.safePageSize());
        LambdaQueryWrapper<OaJobPosition> wrapper = buildQueryWrapper(query);
        wrapper.orderByAsc(OaJobPosition::getPositionSort);
        Page<OaJobPosition> result = baseMapper.selectPage(mpPage, wrapper);
        List<OaJobPositionVo> records = MapstructUtils.convert(result.getRecords(), OaJobPositionVo.class);
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<OaJobPositionVo> selectPositions(OaJobPositionBo query) {
        return baseMapper.selectPositionList(buildQueryWrapper(query).orderByAsc(OaJobPosition::getPositionSort));
    }

    @Override
    public OaJobPositionVo selectPositionById(Long id) {
        OaJobPositionVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("岗位不存在", 404);
        }
        return vo;
    }

    @Override
    public boolean checkCodeUnique(OaJobPositionBo bo) {
        return !baseMapper.exists(new LambdaQueryWrapper<OaJobPosition>()
            .eq(OaJobPosition::getPositionCode, bo.getPositionCode())
            .ne(ObjectUtil.isNotNull(bo.getId()), OaJobPosition::getId, bo.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaJobPositionVo insertPosition(OaJobPositionBo bo) {
        if (!checkCodeUnique(bo)) {
            throw new ServiceException("岗位编码'" + bo.getPositionCode() + "'已存在", 409);
        }
        OaJobPosition position = MapstructUtils.convert(bo, OaJobPosition.class);
        baseMapper.insert(position);
        return baseMapper.selectVoById(position.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaJobPositionVo updatePosition(Long id, OaJobPositionBo bo) {
        OaJobPosition existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("岗位不存在", 404);
        }
        bo.setId(id);
        if (!checkCodeUnique(bo)) {
            throw new ServiceException("岗位编码'" + bo.getPositionCode() + "'已存在", 409);
        }
        if ("1".equals(bo.getStatus()) && countEmployeeRefs(id) > 0) {
            throw new ServiceException("岗位仍有员工引用，不能停用", 409);
        }
        OaJobPosition position = MapstructUtils.convert(bo, OaJobPosition.class);
        baseMapper.updateById(position);
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePosition(Long id) {
        if (baseMapper.selectById(id) == null) {
            throw new ServiceException("岗位不存在", 404);
        }
        if (countEmployeeRefs(id) > 0) {
            throw new ServiceException("岗位存在员工引用，不能删除", 409);
        }
        baseMapper.deleteById(id);
    }

    @Override
    public Map<String, Long> selectPositionIdByCode() {
        List<OaJobPosition> positions = baseMapper.selectList(new LambdaQueryWrapper<OaJobPosition>()
            .select(OaJobPosition::getId, OaJobPosition::getPositionCode));
        Map<String, Long> byCode = new HashMap<>();
        for (OaJobPosition position : positions) {
            if (StringUtils.isNotBlank(position.getPositionCode())) {
                byCode.put(position.getPositionCode().trim(), position.getId());
            }
        }
        return byCode;
    }

    private long countEmployeeRefs(Long positionId) {
        return employeeMapper.selectCount(new LambdaQueryWrapper<OaEmployee>().eq(OaEmployee::getPostId, positionId));
    }

    private LambdaQueryWrapper<OaJobPosition> buildQueryWrapper(OaJobPositionBo query) {
        LambdaQueryWrapper<OaJobPosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(query.getPositionCode()), OaJobPosition::getPositionCode, query.getPositionCode())
            .like(StringUtils.isNotBlank(query.getPositionName()), OaJobPosition::getPositionName, query.getPositionName())
            .eq(StringUtils.isNotBlank(query.getStatus()), OaJobPosition::getStatus, query.getStatus());
        return wrapper;
    }
}
