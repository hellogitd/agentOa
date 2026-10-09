package org.dromara.agentoa.ai.service.support;

import java.util.Date;
import java.util.Set;

/**
 * 知识文档访问抽象（docs/21 AI-M3-02/07）：数据源正文读取与可见范围过滤。
 * <p>
 * 生产实现复用知识库 ACL（{@code KnowledgeAuthorization}）；测试注入桩实现。
 */
public interface KbDocumentAccess {

    /** 文档正文（title + 去标记文本） */
    record DocumentContent(long docId, String title, String text, Date updateTime) {
    }

    /** 提问人可见的文档 ID 集合（服务端过滤依据） */
    Set<Long> visibleDocumentIds(Long userId);

    /** 读取文档正文；不存在或已删除返回 null */
    DocumentContent load(long docId);

    /** 提问人是否可见该文档 */
    boolean canView(Long userId, long docId);
}
