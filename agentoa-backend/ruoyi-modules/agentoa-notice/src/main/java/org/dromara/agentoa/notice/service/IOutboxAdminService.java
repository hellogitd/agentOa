package org.dromara.agentoa.notice.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.vo.OutboxEventVo;

/**
 * outbox 事件管理（docs/15）：失败事件可查询、可人工重投并留下审计。
 */
public interface IOutboxAdminService {

    PageVo<OutboxEventVo> list(String status, NoticePageQuery page);

    void redeliver(Long id, Long operatorUserId);
}
