package org.dromara.agentoa.notice.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.agentoa.notice.domain.OaScheduledPush;
import org.dromara.agentoa.notice.domain.vo.ScheduledPushVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;
import java.util.Map;

public interface OaScheduledPushMapper extends BaseMapperPlus<OaScheduledPush, ScheduledPushVo> {

    /** 到期任务租约抢占（FOR UPDATE SKIP LOCKED，与 OutboxDispatcher 同款 DB 租约） */
    @Select("SELECT id, next_run_time AS nextRunTime FROM oa_scheduled_push "
        + "WHERE status = 1 AND next_run_time IS NOT NULL AND next_run_time <= CURRENT_TIMESTAMP "
        + "ORDER BY next_run_time, id LIMIT 1 FOR UPDATE SKIP LOCKED")
    List<Map<String, Object>> claimDue();
}
