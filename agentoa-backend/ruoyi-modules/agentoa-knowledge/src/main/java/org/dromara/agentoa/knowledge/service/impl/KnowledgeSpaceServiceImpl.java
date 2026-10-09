package org.dromara.agentoa.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentFile;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeMember;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeSpace;
import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.bo.SpacePageQuery;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.domain.vo.MemberVo;
import org.dromara.agentoa.knowledge.domain.vo.SpaceVo;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentFileMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeMemberMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeSpaceMapper;
import org.dromara.agentoa.knowledge.service.IKnowledgeSpaceService;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 空间实现：默认无权限，创建人自动成为 OWNER；成员授权留痕（授权人、时间）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeSpaceServiceImpl implements IKnowledgeSpaceService {

    private final OaKnowledgeSpaceMapper spaceMapper;
    private final OaKnowledgeMemberMapper memberMapper;
    private final OaDocumentMapper documentMapper;
    private final OaDocumentFileMapper fileMapper;
    private final KnowledgeIdentityMapper identityMapper;
    private final KnowledgeAuthorization authorization;

    @Override
    public PageVo<SpaceVo> list(SpacePageQuery query, Long actorUserId) {
        Set<Long> ids = authorization.authorizedSpaceIds(actorUserId);
        if (ids.isEmpty()) {
            return PageVo.of(List.of(), 0, query.safePageNum(), query.safePageSize());
        }
        LambdaQueryWrapper<OaKnowledgeSpace> wrapper = new LambdaQueryWrapper<OaKnowledgeSpace>()
            .in(OaKnowledgeSpace::getId, ids);
        if (query.getSpaceType() != null) {
            wrapper.eq(OaKnowledgeSpace::getSpaceType, query.getSpaceType());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaKnowledgeSpace::getName, query.getKeyword().trim());
        }
        wrapper.orderByDesc(OaKnowledgeSpace::getCreateTime);
        IPage<OaKnowledgeSpace> result = spaceMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<SpaceVo> records = new ArrayList<>();
        for (OaKnowledgeSpace space : result.getRecords()) {
            records.add(toVo(space, actorUserId));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public SpaceVo detail(Long id, Long actorUserId) {
        OaKnowledgeSpace space = authorization.requireSpace(id);
        if (!KnowledgeAccessPolicy.canView(authorization.spaceRole(actorUserId, space))) {
            throw new ServiceException("KN_NOT_FOUND 知识空间不存在或无权访问", 404);
        }
        return toVo(space, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceVo create(SpaceBo bo, Long actorUserId) {
        OaKnowledgeSpace space = new OaKnowledgeSpace();
        apply(space, bo);
        space.setCreateBy(actorUserId);
        space.setCreateDept(null);
        space.setCreateTime(new Date());
        space.setUpdateTime(new Date());
        spaceMapper.insert(space);
        OaKnowledgeMember member = new OaKnowledgeMember();
        member.setSpaceId(space.getId());
        member.setUserId(actorUserId);
        member.setRole(SpaceRole.OWNER.code());
        member.setGrantedBy(actorUserId);
        member.setGrantedTime(new Date());
        member.setCreateTime(new Date());
        memberMapper.insert(member);
        return toVo(space, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceVo update(Long id, SpaceBo bo, Long actorUserId) {
        OaKnowledgeSpace space = authorization.requireSpace(id);
        if (!KnowledgeAccessPolicy.canManage(authorization.spaceRole(actorUserId, space))) {
            throw new ServiceException("KN_FORBIDDEN 仅空间拥有者可修改空间", 403);
        }
        apply(space, bo);
        space.setUpdateBy(actorUserId);
        space.setUpdateTime(new Date());
        spaceMapper.updateById(space);
        return toVo(space, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaKnowledgeSpace space = authorization.requireSpace(id);
        if (!KnowledgeAccessPolicy.canManage(authorization.spaceRole(actorUserId, space))) {
            throw new ServiceException("KN_FORBIDDEN 仅空间拥有者可删除空间", 403);
        }
        Long aliveDocs = documentMapper.selectCount(new LambdaQueryWrapper<OaDocument>()
            .eq(OaDocument::getSpaceId, id));
        Long aliveFiles = fileMapper.selectCount(new LambdaQueryWrapper<OaDocumentFile>()
            .eq(OaDocumentFile::getSpaceId, id));
        if (aliveDocs > 0 || aliveFiles > 0) {
            throw new ServiceException("KN_STATE_CONFLICT 空间仍有文档或文件，请先清空", 409);
        }
        memberMapper.delete(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, id));
        spaceMapper.deleteById(id);
    }

    @Override
    public List<MemberVo> members(Long spaceId, Long actorUserId) {
        OaKnowledgeSpace space = authorization.requireSpace(spaceId);
        if (!KnowledgeAccessPolicy.canView(authorization.spaceRole(actorUserId, space))) {
            throw new ServiceException("KN_NOT_FOUND 知识空间不存在或无权访问", 404);
        }
        List<MemberVo> list = new ArrayList<>();
        for (OaKnowledgeMember member : memberMapper.selectList(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, spaceId)
            .orderByAsc(OaKnowledgeMember::getRole))) {
            list.add(toVo(member));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVo addMember(Long spaceId, MemberBo bo, Long actorUserId) {
        requireManage(spaceId, actorUserId);
        if (identityMapper.existsUser(bo.getUserId()) == 0) {
            throw new ServiceException("KN_NOT_FOUND 账号不存在", 404);
        }
        OaKnowledgeMember member = new OaKnowledgeMember();
        member.setSpaceId(spaceId);
        member.setUserId(bo.getUserId());
        member.setRole(SpaceRole.from(bo.getRole()).code());
        member.setGrantedBy(actorUserId);
        member.setGrantedTime(new Date());
        member.setCreateTime(new Date());
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("KN_STATE_CONFLICT 成员已存在，请使用改角色", 409);
        }
        return toVo(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVo updateMember(Long spaceId, Long userId, MemberBo bo, Long actorUserId) {
        requireManage(spaceId, actorUserId);
        OaKnowledgeMember member = requireMember(spaceId, userId);
        if (!bo.getUserId().equals(userId)) {
            throw new ServiceException("KN_PARAM_MISMATCH 路径与载荷账号ID不一致", 400);
        }
        if (SpaceRole.OWNER.code().equals(member.getRole())
            && !SpaceRole.OWNER.code().equals(bo.getRole())
            && ownerCount(spaceId) <= 1) {
            throw new ServiceException("KN_STATE_CONFLICT 至少保留一名空间拥有者", 409);
        }
        member.setRole(SpaceRole.from(bo.getRole()).code());
        member.setGrantedBy(actorUserId);
        member.setGrantedTime(new Date());
        memberMapper.updateById(member);
        return toVo(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long spaceId, Long userId, Long actorUserId) {
        requireManage(spaceId, actorUserId);
        OaKnowledgeMember member = requireMember(spaceId, userId);
        if (SpaceRole.OWNER.code().equals(member.getRole()) && ownerCount(spaceId) <= 1) {
            throw new ServiceException("KN_STATE_CONFLICT 至少保留一名空间拥有者", 409);
        }
        memberMapper.deleteById(member.getId());
    }

    // ------------------------------------------------------------ internal

    private void requireManage(Long spaceId, Long actorUserId) {
        OaKnowledgeSpace space = authorization.requireSpace(spaceId);
        if (!KnowledgeAccessPolicy.canManage(authorization.spaceRole(actorUserId, space))) {
            throw new ServiceException("KN_FORBIDDEN 仅空间拥有者可管理成员", 403);
        }
    }

    private OaKnowledgeMember requireMember(Long spaceId, Long userId) {
        OaKnowledgeMember member = memberMapper.selectOne(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, spaceId)
            .eq(OaKnowledgeMember::getUserId, userId));
        if (member == null) {
            throw new ServiceException("KN_NOT_FOUND 成员不存在", 404);
        }
        return member;
    }

    private long ownerCount(Long spaceId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, spaceId)
            .eq(OaKnowledgeMember::getRole, SpaceRole.OWNER.code()));
    }

    private void apply(OaKnowledgeSpace space, SpaceBo bo) {
        space.setName(bo.getName().trim());
        space.setIcon(bo.getIcon());
        space.setDescription(bo.getDescription());
        space.setSpaceType(Integer.valueOf(bo.getSpaceType()));
        space.setRemark(bo.getRemark());
    }

    private SpaceVo toVo(OaKnowledgeSpace space, Long actorUserId) {
        SpaceVo vo = new SpaceVo();
        vo.setId(space.getId());
        vo.setName(space.getName());
        vo.setIcon(space.getIcon());
        vo.setDescription(space.getDescription());
        vo.setSpaceType(space.getSpaceType());
        SpaceRole role = authorization.spaceRole(actorUserId, space);
        vo.setMyRole(role == null ? null : role.code());
        vo.setMemberCount(memberMapper.selectCount(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, space.getId())).intValue());
        vo.setCreateBy(space.getCreateBy());
        vo.setCreateByName(identityMapper.selectNickName(space.getCreateBy()));
        vo.setCreateTime(String.valueOf(space.getCreateTime()));
        vo.setUpdateTime(space.getUpdateTime() == null ? null : String.valueOf(space.getUpdateTime()));
        vo.setRemark(space.getRemark());
        return vo;
    }

    private MemberVo toVo(OaKnowledgeMember member) {
        MemberVo vo = new MemberVo();
        vo.setId(member.getId());
        vo.setSpaceId(member.getSpaceId());
        vo.setUserId(member.getUserId());
        vo.setNickname(identityMapper.selectNickName(member.getUserId()));
        vo.setRole(member.getRole());
        vo.setGrantedBy(member.getGrantedBy());
        vo.setGrantedByName(identityMapper.selectNickName(member.getGrantedBy()));
        vo.setGrantedTime(member.getGrantedTime() == null ? null : String.valueOf(member.getGrantedTime()));
        return vo;
    }
}
