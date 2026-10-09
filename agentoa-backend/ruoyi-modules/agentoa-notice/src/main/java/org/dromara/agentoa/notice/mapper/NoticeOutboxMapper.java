package org.dromara.agentoa.notice.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.agentoa.notice.domain.vo.OutboxEventVo;

import java.util.List;

/**
 * sys_outbox 失败事件查询与人工重投（docs/15 第 5 步）。
 */
public interface NoticeOutboxMapper {

    @Select("<script>"
        + "SELECT o.id AS id, o.event_id AS eventId, o.receiver_id AS receiverId, o.event_type AS eventType, "
        + "o.status AS status, o.retry_count AS retryCount, o.redeliver_count AS redeliverCount, "
        + "o.redelivered_by AS redeliveredBy, o.redelivered_time AS redeliveredTime, "
        + "o.next_attempt_time AS nextAttemptTime, o.last_error AS lastError, "
        + "o.create_time AS createTime, o.processed_time AS processedTime, o.payload AS payload "
        + "FROM sys_outbox o "
        + "<where>"
        + "<if test='status != null and status != \"\"'> o.status = #{status} </if>"
        + "</where>"
        + "ORDER BY o.id DESC LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<OutboxEventVo> selectPage(@Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>"
        + "SELECT COUNT(*) FROM sys_outbox o "
        + "<where>"
        + "<if test='status != null and status != \"\"'> o.status = #{status} </if>"
        + "</where>"
        + "</script>")
    long count(@Param("status") String status);

    /** 人工重投：仅 FAILED 事件可重投，重置退避并记录审计列 */
    @Update("UPDATE sys_outbox SET status = 'PENDING', retry_count = 0, next_attempt_time = CURRENT_TIMESTAMP, "
        + "redeliver_count = redeliver_count + 1, redelivered_by = #{operatorId}, redelivered_time = CURRENT_TIMESTAMP "
        + "WHERE id = #{id} AND status = 'FAILED'")
    int redeliver(@Param("id") Long id, @Param("operatorId") Long operatorId);
}
