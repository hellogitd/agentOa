package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 分块向量热缓存（docs/21 AI-M3-04）：按知识域缓存向量快照，索引写入时失效。
 * <p>
 * P0 进程内缓存满足 10–200 人规模；规模化后按 §9.2 切换 Redis/专用向量库（store_type 预留）。
 */
@Component
public class KbVectorCache {

    /** 一个知识域的向量快照（chunkId/sourceId/vector 顺序一致） */
    public record Snapshot(List<Long> chunkIds, List<Long> sourceIds, List<float[]> vectors) {
    }

    private final Map<Long, Snapshot> snapshots = new ConcurrentHashMap<>();

    /** 命中缓存，未命中加载并回填 */
    public Snapshot snapshot(Long kbId, Supplier<Snapshot> loader) {
        Snapshot cached = snapshots.get(kbId);
        if (cached != null) {
            return cached;
        }
        Snapshot loaded = loader.get();
        if (loaded == null) {
            loaded = new Snapshot(List.of(), List.of(), List.of());
        }
        snapshots.put(kbId, loaded);
        return loaded;
    }

    /** 索引写入/数据源变更后失效 */
    public void invalidate(Long kbId) {
        if (kbId != null) {
            snapshots.remove(kbId);
        }
    }

    public void invalidateAll() {
        snapshots.clear();
    }

    /** 从分块行构建快照 */
    public static Snapshot build(List<OaAiKbChunk> chunks) {
        List<Long> chunkIds = new ArrayList<>();
        List<Long> sourceIds = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        if (chunks != null) {
            for (OaAiKbChunk chunk : chunks) {
                float[] vector = KbVectors.fromBytes(chunk.getEmbedding());
                if (vector == null) {
                    continue;
                }
                chunkIds.add(chunk.getId());
                sourceIds.add(chunk.getSourceId());
                vectors.add(vector);
            }
        }
        return new Snapshot(chunkIds, sourceIds, vectors);
    }
}
