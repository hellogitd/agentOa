package org.dromara.agentoa.knowledge;

import org.dromara.agentoa.knowledge.domain.bo.DocumentBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentPageQuery;
import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVo;
import org.dromara.agentoa.knowledge.support.KnowledgeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文档版本并发冲突、历史不可覆盖、回收站恢复与授权过滤搜索。
 */
class KnowledgeDocumentH2Test {

    private static final Set<String> DOC_PERMS = Set.of("kn:space:add", "kn:member:add",
        "kn:doc:add", "kn:doc:edit", "kn:doc:remove", "kn:doc:restore");

    private Long spaceId;

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
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, DOC_PERMS);
        SpaceBo space = new SpaceBo();
        space.setName("知识空间");
        space.setSpaceType("3");
        spaceId = KnowledgeTestEnvironment.spaceService.create(space, KnowledgeTestEnvironment.USER_OWNER).getId();
        addMember(KnowledgeTestEnvironment.USER_EDITOR, "EDITOR");
        addMember(KnowledgeTestEnvironment.USER_VIEWER, "VIEWER");
    }

    @Test
    void createGeneratesFirstVersion() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("项目管理最佳实践", "# 项目管理"), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(doc.getVersion()).isEqualTo(1);
        assertThat(KnowledgeTestEnvironment.documentService.versions(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR))
            .hasSize(1);
    }

    @Test
    void staleBaseVersionConflictsWith409() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("并发文档", "v1"), KnowledgeTestEnvironment.USER_EDITOR);

        DocumentBo first = document("并发文档", "v2");
        first.setBaseVersion(1);
        DocumentVo updated = KnowledgeTestEnvironment.documentService.update(doc.getId(), first, KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(updated.getVersion()).isEqualTo(2);

        DocumentBo stale = document("并发文档", "v2-他人");
        stale.setBaseVersion(1);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.update(doc.getId(), stale, KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("VERSION_CONFLICT");
    }

    @Test
    void historyVersionsAreImmutable() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("历史文档", "第一版正文"), KnowledgeTestEnvironment.USER_EDITOR);
        DocumentBo update = document("历史文档", "第二版正文");
        update.setBaseVersion(1);
        KnowledgeTestEnvironment.documentService.update(doc.getId(), update, KnowledgeTestEnvironment.USER_EDITOR);

        var v1 = KnowledgeTestEnvironment.documentService.version(doc.getId(), 1, KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(v1.getContent()).isEqualTo("第一版正文");
        var v2 = KnowledgeTestEnvironment.documentService.version(doc.getId(), 2, KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(v2.getContent()).isEqualTo("第二版正文");
        assertThat(KnowledgeTestEnvironment.documentService.versions(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR))
            .hasSize(2);
    }

    @Test
    void roleMatrixOnDocument() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("矩阵文档", "内容"), KnowledgeTestEnvironment.USER_EDITOR);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_VIEWER, DOC_PERMS);
        assertThat(KnowledgeTestEnvironment.documentService.detail(doc.getId(), KnowledgeTestEnvironment.USER_VIEWER)).isNotNull();
        DocumentBo edit = document("矩阵文档", "越权修改");
        edit.setBaseVersion(1);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.update(doc.getId(), edit, KnowledgeTestEnvironment.USER_VIEWER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FORBIDDEN");

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OUTSIDER, DOC_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.detail(doc.getId(), KnowledgeTestEnvironment.USER_OUTSIDER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_NOT_FOUND");
    }

    @Test
    void recycleBinRestoreAndSearchVisibility() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("搜索用文档", "项目管理是核心能力"), KnowledgeTestEnvironment.USER_EDITOR);

        DocumentPageQuery query = new DocumentPageQuery();
        query.setKeyword("项目管理");
        assertThat(KnowledgeTestEnvironment.documentService.search(query, KnowledgeTestEnvironment.USER_EDITOR).getRecords())
            .extracting("documentId").containsExactly(doc.getId());

        KnowledgeTestEnvironment.documentService.delete(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(KnowledgeTestEnvironment.documentService.search(query, KnowledgeTestEnvironment.USER_EDITOR).getRecords())
            .isEmpty();

        DocumentPageQuery trash = new DocumentPageQuery();
        trash.setDeleted(true);
        assertThat(KnowledgeTestEnvironment.documentService.list(trash, KnowledgeTestEnvironment.USER_EDITOR).getRecords())
            .hasSize(1);

        KnowledgeTestEnvironment.documentService.restore(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(KnowledgeTestEnvironment.documentService.search(query, KnowledgeTestEnvironment.USER_EDITOR).getRecords())
            .hasSize(1);
    }

    @Test
    void restoreRejectsExpiredEntries() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("过期文档", "内容"), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.documentService.delete(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_document SET deleted_at = ? WHERE id = ?",
            new Date(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000), doc.getId());

        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.restore(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_STATE_CONFLICT");
    }

    @Test
    void documentAclGrantAddsAccessWithoutMembership() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("授权文档", "受限内容"), KnowledgeTestEnvironment.USER_EDITOR);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OUTSIDER, DOC_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.detail(doc.getId(), KnowledgeTestEnvironment.USER_OUTSIDER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_NOT_FOUND");

        KnowledgeTestEnvironment.inTransaction(() -> {
            org.dromara.agentoa.knowledge.domain.OaDocumentAcl acl = new org.dromara.agentoa.knowledge.domain.OaDocumentAcl();
            acl.setDocumentId(doc.getId());
            acl.setSubjectType("USER");
            acl.setSubjectId(KnowledgeTestEnvironment.USER_OUTSIDER);
            acl.setRole("VIEWER");
            acl.setGrantedBy(KnowledgeTestEnvironment.USER_EDITOR);
            acl.setGrantedTime(new Date());
            KnowledgeTestEnvironment.acls.insert(acl);
            return null;
        });

        DocumentVo granted = KnowledgeTestEnvironment.documentService.detail(doc.getId(), KnowledgeTestEnvironment.USER_OUTSIDER);
        assertThat(granted.getMyRole()).isEqualTo(SpaceRole.VIEWER.code());
        DocumentBo edit = document("授权文档", "越权改写");
        edit.setBaseVersion(1);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.update(doc.getId(), edit, KnowledgeTestEnvironment.USER_OUTSIDER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FORBIDDEN");
    }

    @Test
    void archivedDocumentRejectsEdits() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("归档文档", "内容"), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.documentService.publish(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.documentService.archive(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR);

        DocumentBo edit = document("归档文档", "改写");
        edit.setBaseVersion(1);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.documentService.update(doc.getId(), edit, KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_STATE_CONFLICT");
    }

    @Test
    void rollbackAppendsNewVersion() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo doc = KnowledgeTestEnvironment.documentService.create(document("回滚文档", "第一版"), KnowledgeTestEnvironment.USER_EDITOR);
        DocumentBo update = document("回滚文档", "第二版");
        update.setBaseVersion(1);
        KnowledgeTestEnvironment.documentService.update(doc.getId(), update, KnowledgeTestEnvironment.USER_EDITOR);

        DocumentVo rolled = KnowledgeTestEnvironment.documentService.rollback(doc.getId(), 1, KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(rolled.getVersion()).isEqualTo(3);
        assertThat(KnowledgeTestEnvironment.documentService.detail(doc.getId(), KnowledgeTestEnvironment.USER_EDITOR).getContent())
            .isEqualTo("第一版");
    }

    @Test
    void batchDetailsFilterByAuthorizationAndDropDeleted() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, DOC_PERMS);
        DocumentVo first = KnowledgeTestEnvironment.documentService.create(document("批量文档一", "内容一"),
            KnowledgeTestEnvironment.USER_EDITOR);
        DocumentVo second = KnowledgeTestEnvironment.documentService.create(document("批量文档二", "内容二"),
            KnowledgeTestEnvironment.USER_EDITOR);

        // 成员按可见范围取回
        var visible = KnowledgeTestEnvironment.documentService.details(
            java.util.List.of(first.getId(), second.getId(), 999999L), KnowledgeTestEnvironment.USER_VIEWER);
        assertThat(visible).extracting(DocumentVo::getId).containsExactly(first.getId(), second.getId());

        // 越权用户静默丢弃（不报错、不泄露）
        assertThat(KnowledgeTestEnvironment.documentService.details(
            java.util.List.of(first.getId(), second.getId()), KnowledgeTestEnvironment.USER_OUTSIDER)).isEmpty();

        // 已删除文档不出现在批量结果里
        KnowledgeTestEnvironment.documentService.delete(second.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        var afterDelete = KnowledgeTestEnvironment.documentService.details(
            java.util.List.of(first.getId(), second.getId()), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(afterDelete).extracting(DocumentVo::getId).containsExactly(first.getId());
    }

    private void addMember(Long userId, String role) {
        MemberBo bo = new MemberBo();
        bo.setUserId(userId);
        bo.setRole(role);
        KnowledgeTestEnvironment.spaceService.addMember(spaceId, bo, KnowledgeTestEnvironment.USER_OWNER);
    }

    private DocumentBo document(String title, String content) {
        DocumentBo bo = new DocumentBo();
        bo.setSpaceId(spaceId);
        bo.setTitle(title);
        bo.setContent(content);
        return bo;
    }
}
