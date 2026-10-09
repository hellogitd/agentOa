package org.dromara.agentoa.knowledge.service.support;

import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentAcl;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeMember;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeSpace;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.enums.SpaceType;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentAclMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeMemberMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeSpaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 空间/文档授权计算（docs/16：任何列表、搜索、历史、下载都必须在服务端按授权过滤）。
 * <p>
 * 默认无权限：管理员也需成为成员或获得文档授权才能读取内容。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeAuthorization {

    private final OaKnowledgeSpaceMapper spaceMapper;
    private final OaKnowledgeMemberMapper memberMapper;
    private final OaDocumentAclMapper aclMapper;
    private final org.dromara.agentoa.knowledge.mapper.OaDocumentMapper documentMapper;
    private final KnowledgeIdentityMapper identityMapper;

    public OaKnowledgeSpace requireSpace(Long spaceId) {
        OaKnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new ServiceException("KN_NOT_FOUND 知识空间不存在", 404);
        }
        return space;
    }

    /** 用户在空间上的有效角色（成员授权 > 空间类型继承），无权限返回 null */
    public SpaceRole spaceRole(Long actorUserId, Long spaceId) {
        OaKnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            return null;
        }
        return spaceRole(actorUserId, space);
    }

    public SpaceRole spaceRole(Long actorUserId, OaKnowledgeSpace space) {
        OaKnowledgeMember member = memberMapper.selectOne(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getSpaceId, space.getId())
            .eq(OaKnowledgeMember::getUserId, actorUserId));
        return KnowledgeAccessPolicy.resolveSpaceRole(actorUserId,
            member == null ? null : member.getRole(),
            space.getSpaceType() == null ? SpaceType.PUBLIC.code() : space.getSpaceType(),
            space.getCreateBy());
    }

    /** 文档有效角色 = 空间角色与祖先链 ACL 取较高者（KB-08 目录继承） */
    public SpaceRole documentRole(Long actorUserId, OaDocument document) {
        List<OaDocument> chain = ancestorChain(document);
        SpaceRole aclRole = null;
        boolean overrideOnly = false;
        for (int i = chain.size() - 1; i >= 0; i--) {
            OaDocument node = chain.get(i);
            for (OaDocumentAcl acl : documentAcls(node.getId(), actorUserId)) {
                if (acl.getOverrideOnly() != null && acl.getOverrideOnly() == 1) {
                    overrideOnly = true;
                    aclRole = SpaceRole.max(aclRole, SpaceRole.from(acl.getRole()));
                } else if (!overrideOnly) {
                    aclRole = SpaceRole.max(aclRole, SpaceRole.from(acl.getRole()));
                }
            }
        }
        if (overrideOnly) {
            return aclRole;
        }
        SpaceRole spaceRole = spaceRole(actorUserId, document.getSpaceId());
        return KnowledgeAccessPolicy.effectiveRole(spaceRole, aclRole);
    }

    /** 沿 parent_id 上溯祖先链（限深 ≤ 10 防环），含自身 */
    private List<OaDocument> ancestorChain(OaDocument document) {
        List<OaDocument> chain = new ArrayList<>();
        OaDocument current = document;
        int depth = 0;
        while (current != null && depth < 10) {
            chain.add(current);
            if (current.getParentId() == null || current.getParentId() == 0) {
                break;
            }
            current = documentMapper.selectById(current.getParentId());
            depth++;
        }
        return chain;
    }

    /** 用户在文档上的授权记录（USER 主体 + 用户所属 ROLE 主体） */
    public List<OaDocumentAcl> documentAcls(Long documentId, Long actorUserId) {
        List<Long> subjectIds = new ArrayList<>();
        subjectIds.add(actorUserId);
        subjectIds.addAll(identityMapper.selectRoleIds(actorUserId));
        return aclMapper.selectList(new LambdaQueryWrapper<OaDocumentAcl>()
            .eq(OaDocumentAcl::getDocumentId, documentId)
            .in(OaDocumentAcl::getSubjectId, subjectIds));
    }

    /** 当前用户可访问的空间：成员空间 + 公开空间 + 自建私密空间 */
    public Set<Long> authorizedSpaceIds(Long actorUserId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (OaKnowledgeMember member : memberMapper.selectList(new LambdaQueryWrapper<OaKnowledgeMember>()
            .eq(OaKnowledgeMember::getUserId, actorUserId))) {
            ids.add(member.getSpaceId());
        }
        for (OaKnowledgeSpace space : spaceMapper.selectList(new LambdaQueryWrapper<OaKnowledgeSpace>()
            .eq(OaKnowledgeSpace::getSpaceType, SpaceType.PUBLIC.code()))) {
            ids.add(space.getId());
        }
        for (OaKnowledgeSpace space : spaceMapper.selectList(new LambdaQueryWrapper<OaKnowledgeSpace>()
            .eq(OaKnowledgeSpace::getSpaceType, SpaceType.PRIVATE.code())
            .eq(OaKnowledgeSpace::getCreateBy, actorUserId))) {
            ids.add(space.getId());
        }
        return ids;
    }

    /** 仅凭文档授权（非空间可见）可访问的文档ID集合 */
    public Set<Long> aclOnlyDocumentIds(Long actorUserId) {
        List<Long> subjectIds = new ArrayList<>();
        subjectIds.add(actorUserId);
        subjectIds.addAll(identityMapper.selectRoleIds(actorUserId));
        Set<Long> ids = new LinkedHashSet<>();
        for (OaDocumentAcl acl : aclMapper.selectList(new LambdaQueryWrapper<OaDocumentAcl>()
            .in(OaDocumentAcl::getSubjectId, subjectIds))) {
            ids.add(acl.getDocumentId());
        }
        return ids;
    }

    public void requireView(Long actorUserId, OaDocument document) {
        if (!KnowledgeAccessPolicy.canView(documentRole(actorUserId, document))) {
            throw new ServiceException("KN_NOT_FOUND 文档不存在或无权访问", 404);
        }
    }

    public void requireEdit(Long actorUserId, OaDocument document) {
        if (!KnowledgeAccessPolicy.canEdit(documentRole(actorUserId, document))) {
            throw new ServiceException("KN_FORBIDDEN 无编辑权限", 403);
        }
    }

    public void requireManage(Long actorUserId, Long spaceId) {
        if (!KnowledgeAccessPolicy.canManage(spaceRole(actorUserId, spaceId))) {
            throw new ServiceException("KN_FORBIDDEN 仅空间拥有者可管理", 403);
        }
    }

    public void requireSpaceView(Long actorUserId, Long spaceId) {
        if (!KnowledgeAccessPolicy.canView(spaceRole(actorUserId, spaceId))) {
            throw new ServiceException("KN_NOT_FOUND 知识空间不存在或无权访问", 404);
        }
    }

    public void requireSpaceEdit(Long actorUserId, Long spaceId) {
        if (!KnowledgeAccessPolicy.canEdit(spaceRole(actorUserId, spaceId))) {
            throw new ServiceException("KN_FORBIDDEN 无空间编辑权限", 403);
        }
    }
}
