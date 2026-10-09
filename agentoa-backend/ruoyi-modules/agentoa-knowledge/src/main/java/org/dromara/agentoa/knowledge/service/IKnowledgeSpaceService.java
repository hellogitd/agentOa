package org.dromara.agentoa.knowledge.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.bo.SpacePageQuery;
import org.dromara.agentoa.knowledge.domain.vo.MemberVo;
import org.dromara.agentoa.knowledge.domain.vo.SpaceVo;

import java.util.List;

/**
 * 知识空间与成员管理（docs/16 步骤 2）。
 */
public interface IKnowledgeSpaceService {

    PageVo<SpaceVo> list(SpacePageQuery query, Long actorUserId);

    SpaceVo detail(Long id, Long actorUserId);

    SpaceVo create(SpaceBo bo, Long actorUserId);

    SpaceVo update(Long id, SpaceBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);

    List<MemberVo> members(Long spaceId, Long actorUserId);

    MemberVo addMember(Long spaceId, MemberBo bo, Long actorUserId);

    MemberVo updateMember(Long spaceId, Long userId, MemberBo bo, Long actorUserId);

    void removeMember(Long spaceId, Long userId, Long actorUserId);
}
