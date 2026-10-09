package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowDelegate;
import org.dromara.agentoa.workflow.domain.bo.DelegateBo;
import org.dromara.agentoa.workflow.domain.vo.DelegateVo;
import org.dromara.agentoa.workflow.mapper.OaFlowDelegateMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IWorkflowDelegateService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 委托代理服务实现。约束：委托只作用于本人；日期区间合法（开始<=结束）；
 * 受托人不能是委托人本人；停用立即生效。
 */
@RequiredArgsConstructor
@Service
public class WorkflowDelegateServiceImpl implements IWorkflowDelegateService {

    private final OaFlowDelegateMapper delegateMapper;
    private final WorkflowIdentityReadMapper identityMapper;

    @Override
    public List<DelegateVo> selectDelegates(Long ownerId) {
        Long userId = LoginHelper.getUserId();
        Long target = ownerId != null ? ownerId : userId;
        if (!target.equals(userId) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("WF_FORBIDDEN 无权查看他人委托", 403);
        }
        return delegateMapper.selectList(new LambdaQueryWrapper<OaFlowDelegate>()
                .eq(OaFlowDelegate::getOwnerId, target)
                .orderByDesc(OaFlowDelegate::getCreateTime))
            .stream().map(this::toVo).toList();
    }

    @Override
    public DelegateVo selectDelegate(Long delegateId) {
        return toVo(requireOwn(delegateId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DelegateVo createDelegate(DelegateBo bo) {
        Long userId = LoginHelper.getUserId();
        validate(bo);
        OaFlowDelegate entity = bo.toEntity();
        entity.setId(null);
        entity.setOwnerId(userId);
        delegateMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DelegateVo updateDelegate(Long delegateId, DelegateBo bo) {
        OaFlowDelegate existing = requireOwn(delegateId);
        validate(bo);
        OaFlowDelegate entity = bo.toEntity();
        entity.setId(existing.getId());
        entity.setOwnerId(existing.getOwnerId());
        delegateMapper.updateById(entity);
        return toVo(delegateMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDelegate(Long delegateId) {
        requireOwn(delegateId);
        delegateMapper.deleteById(delegateId);
    }

    // ---------------------------------------------------------------- 内部方法

    private void validate(DelegateBo bo) {
        if (bo.getStartDate() == null || bo.getEndDate() == null) {
            throw new ServiceException("委托日期不能为空", 400);
        }
        if (bo.getEndDate().isBefore(bo.getStartDate())) {
            throw new ServiceException("结束日期不能早于开始日期", 400);
        }
        Long userId = LoginHelper.getUserId();
        if (userId.equals(bo.getDelegateId())) {
            throw new ServiceException("受托人不能是本人", 400);
        }
        if (identityMapper.selectUserName(bo.getDelegateId()) == null) {
            throw new ServiceException("受托人不存在", 404);
        }
    }

    private OaFlowDelegate requireOwn(Long delegateId) {
        OaFlowDelegate entity = delegateMapper.selectById(delegateId);
        if (entity == null) {
            throw new ServiceException("WF_NOT_FOUND 委托不存在", 404);
        }
        Long userId = LoginHelper.getUserId();
        if (!entity.getOwnerId().equals(userId) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("WF_FORBIDDEN 无权操作他人委托", 403);
        }
        return entity;
    }

    private DelegateVo toVo(OaFlowDelegate entity) {
        if (entity == null) {
            return null;
        }
        DelegateVo vo = new DelegateVo();
        vo.setId(entity.getId());
        vo.setOwnerId(entity.getOwnerId());
        vo.setOwnerName(identityMapper.selectNickName(entity.getOwnerId()));
        vo.setDelegateId(entity.getDelegateId());
        vo.setDelegateName(identityMapper.selectNickName(entity.getDelegateId()));
        vo.setStartDate(entity.getStartDate());
        vo.setEndDate(entity.getEndDate());
        vo.setProcessKeys(entity.getProcessKeys());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
