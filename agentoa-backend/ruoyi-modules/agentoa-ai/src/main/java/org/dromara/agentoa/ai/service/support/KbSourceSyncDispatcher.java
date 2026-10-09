package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.mapper.OaAiKbChunkMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbSourceMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文档来源增量同步（docs/21 AI-M3-03）：文档新版本触发重索引，删除/不可见触发失效。
 * <p>
 * 知识库暂无领域事件，采用定时对账（同 OutboxDispatcher 轮询模式）；单源失败不影响其他源。
 */
@Component
@RequiredArgsConstructor
public class KbSourceSyncDispatcher {

    private final OaAiKbSourceMapper sourceMapper;
    private final OaAiKbChunkMapper chunkMapper;
    private final KbDocumentAccess documentAccess;
    private final KbIndexer indexer;

    @Scheduled(fixedDelay = 60_000L)
    public void scheduled() {
        syncOnce();
    }

    /** 对账一次（测试与运维手动触发） */
    public void syncOnce() {
        for (OaAiKbSource source : sourceMapper.selectList(new LambdaQueryWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getSourceType, OaAiKbSource.TYPE_DOCUMENT)
            .ne(OaAiKbSource::getIndexStatus, OaAiKbSource.STATUS_INDEXING))) {
            try {
                KbDocumentAccess.DocumentContent content = documentAccess.load(source.getDocId());
                if (content == null) {
                    invalidate(source);
                    continue;
                }
                boolean changed = source.getIndexedAt() == null
                    || (content.updateTime() != null && content.updateTime().after(source.getIndexedAt()));
                if (changed) {
                    Long operatorId = source.getUpdateBy() == null ? source.getCreateBy() : source.getUpdateBy();
                    indexer.indexNow(source, operatorId, null);
                }
            } catch (RuntimeException ignored) {
                // 单源失败已置 failed，可人工重试；不影响其他源对账
            }
        }
    }

    /** 源文档删除/不可见：删除分块（失效）并标记失败原因 */
    private void invalidate(OaAiKbSource source) {
        chunkMapper.delete(new LambdaQueryWrapper<OaAiKbChunk>().eq(OaAiKbChunk::getSourceId, source.getId()));
        source.setIndexStatus(OaAiKbSource.STATUS_FAILED);
        source.setErrorMsg("源文档已删除或不可见");
        source.setChunkCount(0);
        sourceMapper.updateById(source);
    }
}
