package org.dromara.agentoa.knowledge.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.FilePageQuery;
import org.dromara.agentoa.knowledge.domain.vo.FileVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文件柜（docs/16 步骤 4）：复用 sys_file，业务绑定 + 下载授权 + 回收站。
 */
public interface IKnowledgeFileService {

    /** 下载/预览载荷 */
    record StoredBlob(String fileName, String contentType, byte[] bytes) {
    }

    PageVo<FileVo> list(FilePageQuery query, Long actorUserId);

    FileVo upload(Long spaceId, Long documentId, MultipartFile file, Long actorUserId);

    StoredBlob download(Long id, Long actorUserId);

    StoredBlob preview(Long id, Long actorUserId);

    void delete(Long id, Long actorUserId);

    FileVo restore(Long id, Long actorUserId);
}
