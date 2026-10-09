package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.domain.bo.AiKbBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSourceBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.enums.AiCapability;
import org.dromara.agentoa.ai.domain.vo.AiKbChunkVo;
import org.dromara.agentoa.ai.domain.vo.AiKbMemberVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSourceVo;
import org.dromara.agentoa.ai.domain.vo.AiKbVo;
import org.dromara.agentoa.ai.mapper.AiIdentityMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbChunkMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbSourceMapper;
import org.dromara.agentoa.ai.service.IAiKbService;
import org.dromara.agentoa.ai.service.support.AiKbVisibility;
import org.dromara.agentoa.ai.service.support.KbDocumentAccess;
import org.dromara.agentoa.ai.service.support.KbFileStore;
import org.dromara.agentoa.ai.service.support.KbIndexer;
import org.dromara.agentoa.ai.service.support.KbRetriever;
import org.dromara.agentoa.ai.service.support.ProviderRegistry;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识域管理实现（docs/21 M3）：可见性服务端过滤（不可见即 404），写操作仅创建者（403）。
 */
@Service
@RequiredArgsConstructor
public class AiKbServiceImpl implements IAiKbService {

    private final OaAiKbMapper kbMapper;
    private final OaAiKbSourceMapper sourceMapper;
    private final OaAiKbChunkMapper chunkMapper;
    private final AiIdentityMapper identityMapper;
    private final ProviderRegistry providerRegistry;
    private final KbDocumentAccess documentAccess;
    private final KbFileStore fileStore;
    private final KbIndexer indexer;
    private final KbRetriever retriever;

    // ---------------------------------------------------------------- knowledge domains

    @Override
    public PageVo<AiKbVo> list(AiKbBo query, AiPageQuery page, Long userId) {
        LambdaQueryWrapper<OaAiKb> wrapper = new LambdaQueryWrapper<OaAiKb>()
            .select(OaAiKb::getId, OaAiKb::getName, OaAiKb::getVisibility, OaAiKb::getMemberScope,
                OaAiKb::getCreateBy, OaAiKb::getStatus)
            .like(query != null && query.getName() != null && !query.getName().isBlank(),
                OaAiKb::getName, query == null || query.getName() == null ? null : query.getName().strip());
        List<Long> visibleIds = new ArrayList<>();
        for (OaAiKb kb : kbMapper.selectList(wrapper)) {
            if (AiKbVisibility.visible(userId, kb)) {
                visibleIds.add(kb.getId());
            }
        }
        visibleIds.sort(Comparator.reverseOrder());
        int total = visibleIds.size();
        int from = Math.min((page.safePageNum() - 1) * page.safePageSize(), total);
        List<Long> pageIds = visibleIds.subList(from, Math.min(total, from + page.safePageSize()));
        List<AiKbVo> records = new ArrayList<>();
        if (!pageIds.isEmpty()) {
            for (OaAiKb kb : kbMapper.selectBatchIds(pageIds)) {
                records.add(toVo(kb, userId));
            }
        }
        return PageVo.of(records, total, page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiKbVo get(Long id, Long userId) {
        return toVo(requireVisible(id, userId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbVo create(AiKbBo bo, Long userId) {
        OaAiKb kb = new OaAiKb();
        kb.setName(bo.getName().strip());
        kb.setDescription(bo.getDescription());
        kb.setVisibility(validateVisibility(bo.getVisibility()));
        kb.setMemberScope(AiKbVisibility.formatMembers(bo.getMemberUserIds()));
        kb.setEmbeddingModelId(validateEmbeddingModel(bo.getEmbeddingModelId()));
        kb.setStatus(validateStatus(bo.getStatus()));
        kb.setRemark(bo.getRemark());
        kbMapper.insert(kb);
        return toVo(kb, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKbVo update(Long id, AiKbBo bo, Long userId) {
        OaAiKb kb = requireOwner(id, userId);
        if (bo.getName() != null && !bo.getName().isBlank()) {
            kb.setName(bo.getName().strip());
        }
        kb.setDescription(bo.getDescription());
        if (bo.getVisibility() != null) {
            kb.setVisibility(validateVisibility(bo.getVisibility()));
        }
        if (bo.getMemberUserIds() != null) {
            kb.setMemberScope(AiKbVisibility.formatMembers(bo.getMemberUserIds()));
        }
        if (bo.getEmbeddingModelId() != null) {
            kb.setEmbeddingModelId(validateEmbeddingModel(bo.getEmbeddingModelId()));
        }
        if (bo.getStatus() != null) {
            kb.setStatus(validateStatus(bo.getStatus()));
        }
        if (bo.getRemark() != null) {
            kb.setRemark(bo.getRemark());
        }
        kbMapper.updateById(kb);
        return toVo(kb, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        OaAiKb kb = requireOwner(id, userId);
        indexer.deleteKb(kb.getId());
        kbMapper.deleteById(kb.getId());
    }

    // ---------------------------------------------------------------- members

    @Override
    public List<AiKbMemberVo> members(Long id, Long userId) {
        OaAiKb kb = requireVisible(id, userId);
        return toMembers(AiKbVisibility.memberIds(kb));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long id, List<Long> userIds, Long userId) {
        OaAiKb kb = requireOwner(id, userId);
        Set<Long> merged = new LinkedHashSet<>(AiKbVisibility.memberIds(kb));
        merged.addAll(userIds);
        kb.setMemberScope(AiKbVisibility.formatMembers(new ArrayList<>(merged)));
        kbMapper.updateById(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long id, Long memberUserId, Long userId) {
        OaAiKb kb = requireOwner(id, userId);
        List<Long> remaining = AiKbVisibility.memberIds(kb).stream()
            .filter(existing -> !existing.equals(memberUserId)).toList();
        kb.setMemberScope(AiKbVisibility.formatMembers(remaining));
        kbMapper.updateById(kb);
    }

    // ---------------------------------------------------------------- sources

    @Override
    public PageVo<AiKbSourceVo> sources(Long id, AiPageQuery page, Long userId) {
        requireVisible(id, userId);
        IPage<OaAiKbSource> result = sourceMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiKbSource>()
                .eq(OaAiKbSource::getKbId, id)
                .orderByDesc(OaAiKbSource::getId));
        List<AiKbSourceVo> records = new ArrayList<>();
        for (OaAiKbSource source : result.getRecords()) {
            records.add(toVo(source));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiKbSourceVo addDocumentSource(Long id, AiKbSourceBo bo, Long userId, String username) {
        OaAiKb kb = requireOwner(id, userId);
        requireEmbeddingModel(kb);
        if (bo.getDocId() == null) {
            throw new ServiceException("AI_KB_SOURCE_INVALID 文档来源缺少 docId", 400);
        }
        KbDocumentAccess.DocumentContent content = documentAccess.load(bo.getDocId());
        if (content == null || !documentAccess.canView(userId, bo.getDocId())) {
            throw new ServiceException("AI_KB_SOURCE_MISSING 源文档不存在或无权访问", 404);
        }
        Long duplicate = sourceMapper.selectCount(new LambdaQueryWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getKbId, id)
            .eq(OaAiKbSource::getSourceType, OaAiKbSource.TYPE_DOCUMENT)
            .eq(OaAiKbSource::getDocId, bo.getDocId()));
        if (duplicate != null && duplicate > 0) {
            throw new ServiceException("AI_KB_DUPLICATE_SOURCE 该来源已存在", 409);
        }
        OaAiKbSource source = new OaAiKbSource();
        source.setKbId(id);
        source.setSourceType(OaAiKbSource.TYPE_DOCUMENT);
        source.setDocId(bo.getDocId());
        source.setTitle(content.title());
        source.setChunkCount(0);
        source.setIndexStatus(OaAiKbSource.STATUS_PENDING);
        sourceMapper.insert(source);
        indexer.submit(source, userId, username);
        return toVo(source);
    }

    @Override
    public AiKbSourceVo addFileSource(Long id, String fileName, byte[] bytes, Long userId, String username) {
        OaAiKb kb = requireOwner(id, userId);
        requireEmbeddingModel(kb);
        KbFileStore.StoredFile stored = fileStore.upload(fileName, bytes, userId);
        OaAiKbSource source = new OaAiKbSource();
        source.setKbId(id);
        source.setSourceType(OaAiKbSource.TYPE_FILE);
        source.setFileId(stored.fileId());
        source.setTitle(stored.fileName());
        source.setChunkCount(0);
        source.setIndexStatus(OaAiKbSource.STATUS_PENDING);
        sourceMapper.insert(source);
        indexer.submit(source, userId, username);
        return toVo(source);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSource(Long id, Long sourceId, Long userId) {
        OaAiKb kb = requireOwner(id, userId);
        OaAiKbSource source = requireSource(kb.getId(), sourceId);
        indexer.deleteSource(source);
    }

    @Override
    public AiKbSourceVo reindex(Long id, Long sourceId, Long userId, String username) {
        OaAiKb kb = requireOwner(id, userId);
        requireEmbeddingModel(kb);
        OaAiKbSource source = requireSource(kb.getId(), sourceId);
        source.setIndexStatus(OaAiKbSource.STATUS_PENDING);
        source.setErrorMsg(null);
        sourceMapper.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getId, source.getId())
            .set(OaAiKbSource::getIndexStatus, OaAiKbSource.STATUS_PENDING)
            .set(OaAiKbSource::getErrorMsg, null));
        indexer.submit(source, userId, username);
        return toVo(source);
    }

    // ---------------------------------------------------------------- search test and chunks

    @Override
    public List<AiKbSearchHitVo> searchTest(Long id, AiKbSearchTestBo bo, Long userId, String username) {
        OaAiKb kb = requireVisible(id, userId);
        requireEmbeddingModel(kb);
        return retriever.search(userId, username, List.of(kb), bo.getQuery(),
            bo.getTopK() == null ? KbRetriever.DEFAULT_TOP_K : bo.getTopK());
    }

    @Override
    public PageVo<AiKbChunkVo> chunks(Long id, Long sourceId, AiPageQuery page, Long userId) {
        requireVisible(id, userId);
        IPage<OaAiKbChunk> result = chunkMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiKbChunk>()
                .select(OaAiKbChunk::getId, OaAiKbChunk::getKbId, OaAiKbChunk::getSourceId,
                    OaAiKbChunk::getSeq, OaAiKbChunk::getHeading, OaAiKbChunk::getContent,
                    OaAiKbChunk::getTokenCount)
                .eq(OaAiKbChunk::getKbId, id)
                .eq(sourceId != null, OaAiKbChunk::getSourceId, sourceId)
                .orderByAsc(OaAiKbChunk::getSourceId)
                .orderByAsc(OaAiKbChunk::getSeq));
        List<AiKbChunkVo> records = new ArrayList<>();
        for (OaAiKbChunk chunk : result.getRecords()) {
            AiKbChunkVo vo = new AiKbChunkVo();
            vo.setId(chunk.getId());
            vo.setKbId(chunk.getKbId());
            vo.setSourceId(chunk.getSourceId());
            vo.setSeq(chunk.getSeq());
            vo.setHeading(chunk.getHeading());
            vo.setContent(chunk.getContent());
            vo.setTokenCount(chunk.getTokenCount());
            records.add(vo);
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<OaAiKb> visibleKbs(Long userId, List<Long> kbIds) {
        List<OaAiKb> result = new ArrayList<>();
        if (kbIds != null && !kbIds.isEmpty()) {
            for (Long kbId : kbIds) {
                result.add(requireVisible(kbId, userId));
            }
            return result;
        }
        for (OaAiKb kb : kbMapper.selectList(new LambdaQueryWrapper<OaAiKb>()
            .eq(OaAiKb::getStatus, OaAiKb.STATUS_ACTIVE))) {
            if (AiKbVisibility.visible(userId, kb)) {
                result.add(kb);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- internals

    private OaAiKb requireVisible(Long id, Long userId) {
        OaAiKb kb = id == null ? null : kbMapper.selectById(id);
        if (kb == null || !AiKbVisibility.visible(userId, kb)) {
            throw new ServiceException("AI_KB_NOT_FOUND 知识域不存在或无权访问", 404);
        }
        return kb;
    }

    private OaAiKb requireOwner(Long id, Long userId) {
        OaAiKb kb = requireVisible(id, userId);
        if (!AiKbVisibility.owner(userId, kb)) {
            throw new ServiceException("AI_KB_FORBIDDEN 仅创建者可管理该知识域", 403);
        }
        return kb;
    }

    private OaAiKbSource requireSource(Long kbId, Long sourceId) {
        OaAiKbSource source = sourceId == null ? null : sourceMapper.selectById(sourceId);
        if (source == null || !kbId.equals(source.getKbId())) {
            throw new ServiceException("AI_KB_SOURCE_NOT_FOUND 数据源不存在", 404);
        }
        return source;
    }

    private void requireEmbeddingModel(OaAiKb kb) {
        if (kb.getEmbeddingModelId() == null) {
            throw new ServiceException("EMBEDDING_MODEL_UNAVAILABLE 知识域未配置向量模型", 400);
        }
    }

    private Long validateEmbeddingModel(Long modelId) {
        if (modelId == null) {
            return null;
        }
        var model = providerRegistry.requireModel(modelId);
        if (!AiCapability.supports(model.getCapability(), AiCapability.EMBEDDING.code())) {
            throw new ServiceException("MODEL_CAPABILITY_MISMATCH 向量模型能力需包含 embedding", 400);
        }
        return modelId;
    }

    private String validateVisibility(String visibility) {
        String value = visibility == null || visibility.isBlank() ? OaAiKb.VISIBILITY_PRIVATE : visibility.strip();
        if (!OaAiKb.VISIBILITY_PRIVATE.equals(value)
            && !OaAiKb.VISIBILITY_MEMBERS.equals(value)
            && !OaAiKb.VISIBILITY_ALL.equals(value)) {
            throw new ServiceException("AI_KB_VISIBILITY_INVALID 可见性取值非法", 400);
        }
        return value;
    }

    private String validateStatus(String status) {
        String value = status == null || status.isBlank() ? OaAiKb.STATUS_ACTIVE : status.strip();
        if (!OaAiKb.STATUS_ACTIVE.equals(value) && !OaAiKb.STATUS_DISABLED.equals(value)) {
            throw new ServiceException("AI_KB_STATUS_INVALID 状态取值非法", 400);
        }
        return value;
    }

    private List<AiKbMemberVo> toMembers(List<Long> userIds) {
        List<AiKbMemberVo> members = new ArrayList<>();
        if (userIds.isEmpty()) {
            return members;
        }
        Map<Long, AiKbMemberVo> byId = new java.util.HashMap<>();
        for (Map<String, Object> row : identityMapper.selectUserBrief(userIds)) {
            AiKbMemberVo vo = new AiKbMemberVo();
            vo.setUserId(row.get("userId") == null ? null : Long.valueOf(String.valueOf(row.get("userId"))));
            vo.setUserName(row.get("userName") == null ? null : String.valueOf(row.get("userName")));
            vo.setNickName(row.get("nickName") == null ? null : String.valueOf(row.get("nickName")));
            byId.put(vo.getUserId(), vo);
        }
        for (Long userId : userIds) {
            AiKbMemberVo vo = byId.get(userId);
            if (vo == null) {
                vo = new AiKbMemberVo();
                vo.setUserId(userId);
            }
            members.add(vo);
        }
        return members;
    }

    private AiKbVo toVo(OaAiKb kb, Long userId) {
        AiKbVo vo = new AiKbVo();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setVisibility(kb.getVisibility());
        vo.setMemberUserIds(AiKbVisibility.memberIds(kb));
        vo.setEmbeddingModelId(kb.getEmbeddingModelId());
        if (kb.getEmbeddingModelId() != null) {
            var model = providerRegistry.requireModel(kb.getEmbeddingModelId());
            vo.setEmbeddingModelName(model.getAlias() == null || model.getAlias().isBlank()
                ? model.getModelKey() : model.getAlias());
        }
        vo.setStatus(kb.getStatus());
        vo.setOwner(AiKbVisibility.owner(userId, kb));
        Long sourceCount = sourceMapper.selectCount(new LambdaQueryWrapper<OaAiKbSource>()
            .eq(OaAiKbSource::getKbId, kb.getId()));
        vo.setSourceCount(sourceCount == null ? 0 : sourceCount.intValue());
        Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<OaAiKbChunk>()
            .eq(OaAiKbChunk::getKbId, kb.getId()));
        vo.setChunkCount(chunkCount == null ? 0 : chunkCount.intValue());
        vo.setRemark(kb.getRemark());
        vo.setCreateTime(kb.getCreateTime());
        vo.setUpdateTime(kb.getUpdateTime());
        return vo;
    }

    private AiKbSourceVo toVo(OaAiKbSource source) {
        AiKbSourceVo vo = new AiKbSourceVo();
        vo.setId(source.getId());
        vo.setKbId(source.getKbId());
        vo.setSourceType(source.getSourceType());
        vo.setDocId(source.getDocId());
        vo.setFileId(source.getFileId());
        vo.setTitle(source.getTitle());
        vo.setChunkCount(source.getChunkCount());
        vo.setIndexStatus(source.getIndexStatus());
        vo.setErrorMsg(source.getErrorMsg());
        vo.setIndexedAt(source.getIndexedAt());
        vo.setCreateTime(source.getCreateTime());
        vo.setUpdateTime(source.getUpdateTime());
        return vo;
    }
}
