package org.dromara.agentoa.knowledge;

import org.dromara.agentoa.knowledge.domain.bo.CommentBo;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.vo.CommentVo;
import org.dromara.agentoa.knowledge.domain.vo.SocialStatVo;
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
 * P1 集成测试（KB-06/KB-07）：评论权限、点赞幂等、收藏与越权。
 */
class KnowledgeP1H2Test {

    private static final Long DOC_ID = 500L;
    private static final Long SPACE_ID = 10L;

    @BeforeAll
    static void boot() {
        KnowledgeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        KnowledgeTestEnvironment.clearData();
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_OWNER, "owner");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_EDITOR, "editor");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_VIEWER, "viewer");
        KnowledgeTestEnvironment.seedUser(KnowledgeTestEnvironment.USER_OUTSIDER, "outsider");
        KnowledgeTestEnvironment.logout();
        KnowledgeTestEnvironment.seedSpace(SPACE_ID, "space", 3, KnowledgeTestEnvironment.USER_OWNER);
        KnowledgeTestEnvironment.seedMember(1L, SPACE_ID, KnowledgeTestEnvironment.USER_EDITOR, SpaceRole.EDITOR);
        KnowledgeTestEnvironment.seedMember(2L, SPACE_ID, KnowledgeTestEnvironment.USER_VIEWER, SpaceRole.VIEWER);
        KnowledgeTestEnvironment.seedDocument(DOC_ID, SPACE_ID, "doc", "content");
    }

    private CommentBo commentBo(String content, Long parentId) {
        CommentBo bo = new CommentBo();
        bo.setContent(content);
        bo.setParentId(parentId);
        return bo;
    }

    @Test
    void commentRequiresCommenterRole() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_VIEWER, Set.of());
        assertThatThrownBy(() -> KnowledgeTestEnvironment.socialService.addComment(DOC_ID, commentBo("hi", null)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FORBIDDEN");

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, Set.of());
        CommentVo vo = KnowledgeTestEnvironment.socialService.addComment(DOC_ID, commentBo("looks good", null));
        assertThat(vo.getContent()).isEqualTo("looks good");
    }

    @Test
    void commentReplyListAndDelete() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, Set.of());
        CommentVo parent = KnowledgeTestEnvironment.socialService.addComment(DOC_ID, commentBo("first", null));
        KnowledgeTestEnvironment.socialService.addComment(DOC_ID, commentBo("reply", parent.getId()));
        List<CommentVo> comments = KnowledgeTestEnvironment.socialService.selectComments(DOC_ID);
        assertThat(comments).hasSize(2);
        assertThat(comments.get(1).getParentId()).isEqualTo(parent.getId());

        // 作者可删，他人不可删
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, Set.of());
        assertThatThrownBy(() -> KnowledgeTestEnvironment.socialService.deleteComment(parent.getId()))
            .isInstanceOf(ServiceException.class);
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, Set.of());
        KnowledgeTestEnvironment.socialService.deleteComment(parent.getId());
        assertThat(KnowledgeTestEnvironment.socialService.selectComments(DOC_ID)).hasSize(1);
    }

    @Test
    void likeIsIdempotent() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, Set.of());
        assertThat(KnowledgeTestEnvironment.socialService.like(DOC_ID)).isEqualTo(1L);
        assertThat(KnowledgeTestEnvironment.socialService.like(DOC_ID)).isEqualTo(1L);
        SocialStatVo stat = KnowledgeTestEnvironment.socialService.stat(DOC_ID);
        assertThat(stat.getLikeCount()).isEqualTo(1L);
        assertThat(stat.getLikedByMe()).isTrue();

        assertThat(KnowledgeTestEnvironment.socialService.unlike(DOC_ID)).isZero();
        assertThat(KnowledgeTestEnvironment.socialService.stat(DOC_ID).getLikedByMe()).isFalse();
    }

    @Test
    void favoriteIsIdempotentAndListed() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, Set.of());
        KnowledgeTestEnvironment.socialService.favorite(DOC_ID);
        KnowledgeTestEnvironment.socialService.favorite(DOC_ID);
        assertThat(KnowledgeTestEnvironment.socialService.myFavorites()).containsExactly(DOC_ID);
        assertThat(KnowledgeTestEnvironment.socialService.stat(DOC_ID).getFavoritedByMe()).isTrue();

        KnowledgeTestEnvironment.socialService.unfavorite(DOC_ID);
        assertThat(KnowledgeTestEnvironment.socialService.myFavorites()).isEmpty();
    }

    @Test
    void outsiderCannotInteract() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OUTSIDER, Set.of());
        assertThatThrownBy(() -> KnowledgeTestEnvironment.socialService.like(DOC_ID))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.socialService.selectComments(DOC_ID))
            .isInstanceOf(ServiceException.class);
    }
}
