package org.dromara.agentoa.knowledge;

import org.dromara.agentoa.knowledge.domain.bo.FilePageQuery;
import org.dromara.agentoa.knowledge.domain.bo.MemberBo;
import org.dromara.agentoa.knowledge.domain.bo.SpaceBo;
import org.dromara.agentoa.knowledge.domain.vo.FileVo;
import org.dromara.agentoa.knowledge.support.KnowledgeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件柜：上传绑定、大小限制、下载授权与审计、回收恢复。
 */
class KnowledgeFileH2Test {

    private static final Set<String> FILE_PERMS = Set.of("kn:space:add", "kn:member:add",
        "kn:doc:add", "kn:doc:edit", "kn:doc:remove", "kn:doc:restore",
        "kn:file:upload", "kn:file:remove");

    private static final byte[] PNG_BYTES = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3, 4};

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
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OWNER, FILE_PERMS);
        SpaceBo space = new SpaceBo();
        space.setName("文件空间");
        space.setSpaceType("3");
        spaceId = KnowledgeTestEnvironment.spaceService.create(space, KnowledgeTestEnvironment.USER_OWNER).getId();
        MemberBo editor = new MemberBo();
        editor.setUserId(KnowledgeTestEnvironment.USER_EDITOR);
        editor.setRole("EDITOR");
        KnowledgeTestEnvironment.spaceService.addMember(spaceId, editor, KnowledgeTestEnvironment.USER_OWNER);
        MemberBo viewer = new MemberBo();
        viewer.setUserId(KnowledgeTestEnvironment.USER_VIEWER);
        viewer.setRole("VIEWER");
        KnowledgeTestEnvironment.spaceService.addMember(spaceId, viewer, KnowledgeTestEnvironment.USER_OWNER);
    }

    @Test
    void uploadBindsBusinessRecord() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        FileVo file = KnowledgeTestEnvironment.fileService.upload(spaceId, null, png("photo.png"), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(file.getFileName()).isEqualTo("photo.png");
        assertThat(file.getFileSize()).isEqualTo((long) PNG_BYTES.length);
        assertThat(file.getContentType()).isEqualTo("image/png");
        assertThat(KnowledgeTestEnvironment.BLOBS).hasSize(1);
    }

    @Test
    void oversizedFileIsRejected() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        byte[] big = new byte[(int) (org.dromara.agentoa.knowledge.service.support.KnowledgeFileStorage.MAX_FILE_BYTES + 1)];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", big);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.fileService.upload(spaceId, null, file, KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FILE_SIZE");
    }

    @Test
    void mismatchedContentIsRejected() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
            "not a png".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> KnowledgeTestEnvironment.fileService.upload(spaceId, null, fake, KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FILE_TYPE");
    }

    @Test
    void downloadIsAuthorizedAndAudited() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        FileVo file = KnowledgeTestEnvironment.fileService.upload(spaceId, null, png("doc.png"), KnowledgeTestEnvironment.USER_EDITOR);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_VIEWER, FILE_PERMS);
        var blob = KnowledgeTestEnvironment.fileService.download(file.getId(), KnowledgeTestEnvironment.USER_VIEWER);
        assertThat(blob.bytes()).isEqualTo(PNG_BYTES);
        assertThat(KnowledgeTestEnvironment.files.selectById(file.getId()).getDownloadCount()).isEqualTo(1);
        assertThat(KnowledgeTestEnvironment.files.selectById(file.getId()).getLastDownloadBy())
            .isEqualTo(KnowledgeTestEnvironment.USER_VIEWER);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_OUTSIDER, FILE_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.fileService.download(file.getId(), KnowledgeTestEnvironment.USER_OUTSIDER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_NOT_FOUND");
    }

    @Test
    void deleteGoesToRecycleBinAndRestores() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        FileVo file = KnowledgeTestEnvironment.fileService.upload(spaceId, null, png("temp.png"), KnowledgeTestEnvironment.USER_EDITOR);

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_VIEWER, FILE_PERMS);
        assertThatThrownBy(() -> KnowledgeTestEnvironment.fileService.delete(file.getId(), KnowledgeTestEnvironment.USER_VIEWER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_FORBIDDEN");

        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        KnowledgeTestEnvironment.fileService.delete(file.getId(), KnowledgeTestEnvironment.USER_EDITOR);

        FilePageQuery alive = new FilePageQuery();
        alive.setSpaceId(spaceId);
        assertThat(KnowledgeTestEnvironment.fileService.list(alive, KnowledgeTestEnvironment.USER_EDITOR).getRecords()).isEmpty();
        FilePageQuery trash = new FilePageQuery();
        trash.setSpaceId(spaceId);
        trash.setDeleted(true);
        assertThat(KnowledgeTestEnvironment.fileService.list(trash, KnowledgeTestEnvironment.USER_EDITOR).getRecords()).hasSize(1);

        KnowledgeTestEnvironment.fileService.restore(file.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(KnowledgeTestEnvironment.fileService.list(alive, KnowledgeTestEnvironment.USER_EDITOR).getRecords()).hasSize(1);
    }

    @Test
    void restoreRejectsExpiredEntries() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        FileVo file = KnowledgeTestEnvironment.fileService.upload(spaceId, null, png("old.png"), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.fileService.delete(file.getId(), KnowledgeTestEnvironment.USER_EDITOR);
        KnowledgeTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_document_file SET deleted_at = ? WHERE id = ?",
            new Date(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000), file.getId());
        assertThatThrownBy(() -> KnowledgeTestEnvironment.fileService.restore(file.getId(), KnowledgeTestEnvironment.USER_EDITOR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("KN_STATE_CONFLICT");
    }

    @Test
    void documentAttachmentRequiresDocEdit() {
        KnowledgeTestEnvironment.loginAs(KnowledgeTestEnvironment.USER_EDITOR, FILE_PERMS);
        org.dromara.agentoa.knowledge.domain.bo.DocumentBo doc = new org.dromara.agentoa.knowledge.domain.bo.DocumentBo();
        doc.setSpaceId(spaceId);
        doc.setTitle("附件文档");
        doc.setContent("内容");
        var created = KnowledgeTestEnvironment.documentService.create(doc, KnowledgeTestEnvironment.USER_EDITOR);

        FileVo attached = KnowledgeTestEnvironment.fileService.upload(spaceId, created.getId(), png("attach.png"),
            KnowledgeTestEnvironment.USER_EDITOR);
        assertThat(attached.getDocumentId()).isEqualTo(created.getId());

        FilePageQuery query = new FilePageQuery();
        query.setDocumentId(created.getId());
        assertThat(KnowledgeTestEnvironment.fileService.list(query, KnowledgeTestEnvironment.USER_EDITOR).getRecords())
            .hasSize(1);
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG_BYTES);
    }
}
