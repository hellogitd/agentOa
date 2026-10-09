package org.dromara.agentoa.knowledge;

import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.policy.KnowledgeAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限矩阵纯函数测试（docs/02 KB-08 / docs/16 角色与继承规则）。
 */
class KnowledgeAccessPolicyTest {

    @Test
    void memberRoleWinsOverSpaceType() {
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, "EDITOR", 1, 9L)).isEqualTo(SpaceRole.EDITOR);
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, "OWNER", 2, 9L)).isEqualTo(SpaceRole.OWNER);
    }

    @Test
    void publicSpaceGrantsViewerWithoutMembership() {
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, null, 1, 9L)).isEqualTo(SpaceRole.VIEWER);
    }

    @Test
    void privateSpaceOnlyCreatorAndTeamOnlyMembers() {
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(9L, null, 2, 9L)).isEqualTo(SpaceRole.OWNER);
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, null, 2, 9L)).isNull();
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, null, 3, 9L)).isNull();
    }

    @Test
    void effectiveRoleTakesTheHigherGrant() {
        assertThat(KnowledgeAccessPolicy.effectiveRole(SpaceRole.VIEWER, SpaceRole.EDITOR))
            .isEqualTo(SpaceRole.EDITOR);
        assertThat(KnowledgeAccessPolicy.effectiveRole(SpaceRole.OWNER, SpaceRole.VIEWER))
            .isEqualTo(SpaceRole.OWNER);
        assertThat(KnowledgeAccessPolicy.effectiveRole(null, SpaceRole.COMMENTER))
            .isEqualTo(SpaceRole.COMMENTER);
    }

    @Test
    void actionMatrix() {
        assertThat(KnowledgeAccessPolicy.canView(SpaceRole.VIEWER)).isTrue();
        assertThat(KnowledgeAccessPolicy.canDownload(SpaceRole.VIEWER)).isTrue();
        assertThat(KnowledgeAccessPolicy.canEdit(SpaceRole.VIEWER)).isFalse();
        assertThat(KnowledgeAccessPolicy.canEdit(SpaceRole.COMMENTER)).isFalse();
        assertThat(KnowledgeAccessPolicy.canEdit(SpaceRole.EDITOR)).isTrue();
        assertThat(KnowledgeAccessPolicy.canManage(SpaceRole.EDITOR)).isFalse();
        assertThat(KnowledgeAccessPolicy.canManage(SpaceRole.OWNER)).isTrue();
        assertThat(KnowledgeAccessPolicy.canView(null)).isFalse();
        assertThat(KnowledgeAccessPolicy.canDownload(null)).isFalse();
    }

    @Test
    void defaultDenyEvenForAdminWithoutGrant() {
        // 管理员也需审计授权：没有成员记录或文档授权时解析不到角色
        assertThat(KnowledgeAccessPolicy.resolveSpaceRole(1L, null, 3, 9L)).isNull();
        assertThat(KnowledgeAccessPolicy.hasPerm(Set.of("kn:space:add"), "kn:space:add")).isTrue();
        assertThat(KnowledgeAccessPolicy.hasPerm(Set.of(), "kn:space:add")).isFalse();
    }
}
