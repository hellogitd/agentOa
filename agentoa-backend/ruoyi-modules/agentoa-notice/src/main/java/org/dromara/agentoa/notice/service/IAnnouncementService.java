package org.dromara.agentoa.notice.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.AnnouncementBo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.vo.AnnouncementVo;
import org.dromara.agentoa.notice.domain.vo.ReadStatusVo;

/**
 * 公告生命周期（docs/15 第 2/3/5 步）：DRAFT/PUBLISHED/RECALLED；
 * 发布冻结内容与受众快照；已发布内容拒绝修改；撤回保留审计与已读记录。
 */
public interface IAnnouncementService {

    PageVo<AnnouncementVo> list(NoticePageQuery query, Long actorUserId);

    AnnouncementVo detail(Long id, Long actorUserId);

    AnnouncementVo create(AnnouncementBo bo, Long publisherId);

    AnnouncementVo update(Long id, AnnouncementBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);

    /** 发布：受众快照 + 站内信 fan-out；Idempotency-Key 重放返回首次结果 */
    AnnouncementVo publish(Long id, String idempotencyKey, Long publisherId);

    void recall(Long id, Long actorUserId);

    ReadStatusVo readStatus(Long id, Long actorUserId);

    void markRead(Long id, Long userId);
}
