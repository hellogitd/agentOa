package org.dromara.agentoa.notice.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.ScheduledPushBo;
import org.dromara.agentoa.notice.domain.vo.PushRunVo;
import org.dromara.agentoa.notice.domain.vo.ScheduledPushVo;

/**
 * 定时推送（NC-04）：定时公告 / 模板推送任务管理与到期执行（调度器与手动端点共用）。
 */
public interface IScheduledPushService {

    PageVo<ScheduledPushVo> list(NoticePageQuery query);

    ScheduledPushVo detail(Long id);

    ScheduledPushVo create(ScheduledPushBo bo, Long operatorUserId);

    ScheduledPushVo update(Long id, ScheduledPushBo bo, Long operatorUserId);

    void delete(Long id);

    void pause(Long id);

    void resume(Long id);

    PageVo<PushRunVo> runs(Long pushId, NoticePageQuery query);

    /** 执行到期任务：DB 租约抢占 + event_key 幂等去重，返回本次成功执行的任务数 */
    int executeDue();
}
