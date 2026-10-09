package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.service.support.KbDocumentAccess.DocumentContent;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.agentoa.knowledge.service.support.MarkdownText;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识文档访问实现（docs/21 AI-M3-07）：复用知识库 ACL 口径——
 * 可见文档 = （space_id ∈ authorizedSpaceIds）∪（id ∈ aclOnlyDocumentIds），
 * 与知识库列表/搜索的服务端过滤公式一致。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentAccess implements KbDocumentAccess {

    private final KnowledgeAuthorization authorization;
    private final OaDocumentMapper documentMapper;

    @Override
    public Set<Long> visibleDocumentIds(Long userId) {
        Set<Long> spaceIds = authorization.authorizedSpaceIds(userId);
        Set<Long> aclIds = authorization.aclOnlyDocumentIds(userId);
        if (spaceIds.isEmpty() && aclIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        if (!spaceIds.isEmpty()) {
            List<OaDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<OaDocument>()
                .select(OaDocument::getId)
                .isNull(OaDocument::getDeletedAt)
                .in(OaDocument::getSpaceId, spaceIds));
            for (OaDocument doc : docs) {
                result.add(doc.getId());
            }
        }
        if (!aclIds.isEmpty()) {
            List<OaDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<OaDocument>()
                .select(OaDocument::getId)
                .isNull(OaDocument::getDeletedAt)
                .in(OaDocument::getId, aclIds));
            for (OaDocument doc : docs) {
                result.add(doc.getId());
            }
        }
        return result;
    }

    @Override
    public DocumentContent load(long docId) {
        OaDocument document = documentMapper.selectById(docId);
        if (document == null || document.getDeletedAt() != null) {
            return null;
        }
        String text = document.getContentText();
        if (text == null || text.isBlank()) {
            text = MarkdownText.strip(document.getContent());
        }
        return new DocumentContent(document.getId(), document.getTitle(),
            text == null ? "" : text, document.getUpdateTime());
    }

    @Override
    public boolean canView(Long userId, long docId) {
        OaDocument document = documentMapper.selectById(docId);
        return document != null && document.getDeletedAt() == null
            && authorization.documentRole(userId, document) != null;
    }
}
