package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.bo.AiKbBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSearchTestBo;
import org.dromara.agentoa.ai.domain.bo.AiKbSourceBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiKbChunkVo;
import org.dromara.agentoa.ai.domain.vo.AiKbMemberVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.domain.vo.AiKbSourceVo;
import org.dromara.agentoa.ai.domain.vo.AiKbVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 知识域管理服务（docs/21 M3）：知识域/成员/数据源/索引/检索测试。
 * <p>
 * 对象权限模式同知识库：不可见即 404，非创建者修改 403。
 */
public interface IAiKbService {

    PageVo<AiKbVo> list(AiKbBo query, AiPageQuery page, Long userId);

    AiKbVo get(Long id, Long userId);

    AiKbVo create(AiKbBo bo, Long userId);

    AiKbVo update(Long id, AiKbBo bo, Long userId);

    void delete(Long id, Long userId);

    List<AiKbMemberVo> members(Long id, Long userId);

    void addMembers(Long id, List<Long> userIds, Long userId);

    void removeMember(Long id, Long memberUserId, Long userId);

    PageVo<AiKbSourceVo> sources(Long id, AiPageQuery page, Long userId);

    /** 知识文档来源（docs/21 AI-M3-02） */
    AiKbSourceVo addDocumentSource(Long id, AiKbSourceBo bo, Long userId, String username);

    /** 直传文件来源（PDF/DOCX/TXT/MD，经 sys_file） */
    AiKbSourceVo addFileSource(Long id, String fileName, byte[] bytes, Long userId, String username);

    void removeSource(Long id, Long sourceId, Long userId);

    /** 重新索引（docs/21 AI-M3-03 增量重索引） */
    AiKbSourceVo reindex(Long id, Long sourceId, Long userId, String username);

    /** 检索测试（docs/21 AI-M3-06） */
    List<AiKbSearchHitVo> searchTest(Long id, AiKbSearchTestBo bo, Long userId, String username);

    /** 分块预览（docs/21 AI-M3-06） */
    PageVo<AiKbChunkVo> chunks(Long id, Long sourceId, AiPageQuery page, Long userId);

    /** 本人可见且启用的知识域（QA 入口） */
    List<OaAiKb> visibleKbs(Long userId, List<Long> kbIds);
}
