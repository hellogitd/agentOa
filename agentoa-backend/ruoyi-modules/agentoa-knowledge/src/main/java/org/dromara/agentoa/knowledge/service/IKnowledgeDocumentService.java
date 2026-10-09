package org.dromara.agentoa.knowledge.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentDraftBo;
import org.dromara.agentoa.knowledge.domain.bo.DocumentPageQuery;
import org.dromara.agentoa.knowledge.domain.vo.DocumentDraftVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVersionVo;
import org.dromara.agentoa.knowledge.domain.vo.DocumentVo;
import org.dromara.agentoa.knowledge.domain.vo.SearchHitVo;

import java.util.List;

/**
 * 知识文档（docs/16 步骤 3/5）：版本提交乐观锁、历史只追加、回收站与搜索。
 */
public interface IKnowledgeDocumentService {

    PageVo<DocumentVo> list(DocumentPageQuery query, Long actorUserId);

    DocumentVo detail(Long id, Long actorUserId);

    /** 批量详情（收藏等列表场景）：服务端按授权过滤，无权限/已删除文档静默丢弃 */
    List<DocumentVo> details(List<Long> ids, Long actorUserId);

    DocumentVo create(DocumentBo bo, Long actorUserId);

    DocumentVo update(Long id, DocumentBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);

    DocumentVo restore(Long id, Long actorUserId);

    List<DocumentVersionVo> versions(Long id, Long actorUserId);

    DocumentVersionVo version(Long id, Integer version, Long actorUserId);

    DocumentVo rollback(Long id, Integer version, Long actorUserId);

    void saveDraft(Long id, DocumentDraftBo bo, Long actorUserId);

    DocumentDraftVo draft(Long id, Long actorUserId);

    DocumentVo publish(Long id, Long actorUserId);

    DocumentVo archive(Long id, Long actorUserId);

    PageVo<SearchHitVo> search(DocumentPageQuery query, Long actorUserId);
}
