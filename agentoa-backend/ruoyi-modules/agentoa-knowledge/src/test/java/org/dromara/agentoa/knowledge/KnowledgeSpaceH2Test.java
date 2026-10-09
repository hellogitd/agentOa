package org.dromara.agentoa.knowledge;

import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.vo.MemberVo;
import org.dromara.agentoa.knowledge.domain.vo.SpaceVo;
import org.dromara.agentoa.knowledge.support.KnowledgeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 空间与成员：授权留痕、越权、成员移除后旧链接失效、最后拥有者保护。
 */
class KnowledgeSpaceH2Test {

    private static final Set<String> SPACE_PERMS = Set.of("kn:space:add", "kn:space:edit",
        "kn:space:remove", "kn:member:add", "kn:member:edit", "kn:member:remove");

    @BeforeAll
    static void boot() {
        KnowledgeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        KnowledgeTestEnvironment.clearData();
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_OWNER, "拥有者");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_EDITOR, "编辑者");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_VIEWER, "查看者");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_OUTSIDER, "外部人员");
        KnowledgeTestEnvironment.logout();
    }

    @Test
    void creatorBecomesOwnerAndListShowsRole() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        SpaceVo space = KnowledgeTestEnvironment.spaceService.create(teamSpace(), KnowledgeTestEnvironment.USER_OWNER);
        assertThat(space.getMyRole()).isEqualTo(SpaceRole.OWNER.code());

        var page = KnowledgeTestEnvironment.spaceService.list(new org.dromara.agentoa.knowledge.domain.bo.SpacePageQuery(),
            KnowledgeTestEnvironment.USER_OWNER);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getMyRole()).isEqualTo(SpaceRole.OWNER.code());
    }

    @Test
    void outsiderCannotSeeTeamSpace() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        SpaceVo space = KnowledgeTestEnvironment.spaceService.create(teamSpace(), KnowledgeTestEnvironment.USER_OWNER);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OUTSIDER, SPACE_PERMS);
        var page = KnowledgeTestEnvironment.spaceService.list(new org.dromara.agentoa.knowledge.domain.bo.SpacePageQuery(),
            KnowledgeTestEnvironment.USER_OUTSIDER);
        assertThat(page.getRecords()).isEmpty();
        assertThatThrownBy(() -> KnowledgeTestEnvironment.spaceService.detail(space.getId(), KnowledgeTestEnvironment.USER_OUTSIDER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_NOT_FOUND");
    }

    @Test
    void onlyOwnerManagesMembers() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        SpaceVo space = KnowledgeTestEnvironment.spaceService.create(teamSpace(), KnowledgeTestEnvironment.USER_OWNER);
        KnowledgeTestEnvironment.spaceService.addMember(space.getId(), member(KnowledgeTestEnvironment.USER_EDITOR, "EDITOR"),
            KnowledgeTestEnvironment.USER_OWNER);
        KnowledgeTestEnvironment.spaceService.addMember(space.getId(), member(KnowledgeTestEnvironment.USER_VIEWER, "VIEWER"),
            KnowledgeTestEnvironment.USER_OWNER);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, SPACE_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.spaceService.addMember(space.getId(),
            member(KnowledgeTestEnvironment.USER_OUTSIDER, "VIEWER"), KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FORBIDDEN");

        List<MemberVo> members = KnowledgeTestEnvironment.spaceService.members(space.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(members).hasSize(3);
    }

    @Test
    void removedMemberLosesAccess() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        SpaceVo space = KnowledgeTestEnvironment.spaceService.create(teamSpace(), KnowledgeTestEnvironment.USER_OWNER);
        KnowledgeTestEnvironment.spaceService.addMember(space.getId(), member(KnowledgeTestEnvironment.USER_EDITOR, "EDITOR"),
            KnowledgeTestEnvironment.USER_OWNER);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, SPACE_PERMS);
        assertThat(KnowledgeTestEnvironment.spaceService.detail(space.getId(), KnowledgeTestEnvironment.USER_EDITOR).getMyRole())
            .isEqualTo(SpaceRole.EDITOR.code());

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        KnowledgeTestEnvironment.spaceService.removeMember(space.getId(), KnowledgeTestEnvironment.USER_EDITOR,
            KnowledgeTestEnvironment.USER_OWNER);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, SPACE_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.spaceService.detail(space.getId(), KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_NOT_FOUND");
    }

    @Test
    void lastOwnerIsProtected() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, SPACE_PERMS);
        SpaceVo space = KnowledgeTestEnvironment.spaceService.create(teamSpace(), KnowledgeTestEnvironment.USER_OWNER);

        MemberBo demote = member(KnowledgeTestEnvironment.USER_OWNER, "VIEWER");
        assertThatThrownBy(() -> KnowledgeTestEnvironment.spaceService.updateMember(space.getId(),
            KnowledgeTestEnvironment.USER_OWNER, demote, KnowledgeTestEnvironment.USER_OWNER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_STATE_CONFLICT");
        assertThatThrownBy(() -> KnowledgeTestEnvironment.spaceService.removeMember(space.getId(),
            KnowledgeTestEnvironment.USER_OWNER, KnowledgeTestEnvironment.USER_OWNER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_STATE_CONFLICT");
    }

    private SpaceBo teamSpace() {
        SpaceBo bo = new SpaceBo();
        bo.setName("团队空间");
        bo.setSpaceType("3");
        bo.setDescription("测试空间");
        return bo;
    }

    private MemberBo member(Long userId, String role) {
        MemberBo bo = new MemberBo();
        bo.setUserId(userId);
        bo.setRole(role);
        return bo;
    }
}
