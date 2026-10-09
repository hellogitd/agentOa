package org.dromara.agentoa.message;

import lombok.RequiredArgsConstructor;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;

/** DB lock makes delivery safe across workers; notification is always durable before push. */
@Component @RequiredArgsConstructor
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TicketWebSocket websocket;
    @Scheduled(fixedDelay=3000) public void dispatch() {
        for(int n=0;n<20;n++) {
            Map<String,Object> result=transactions.execute(status -> {
                var events=jdbc.queryForList("SELECT * FROM sys_outbox WHERE status='PENDING' AND next_attempt_time<=CURRENT_TIMESTAMP ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED");
                if(events.isEmpty()) return null;
                var event=events.get(0);
                try {
                    var payload=JsonUtils.parseMap(event.get("payload").toString());
                    jdbc.update("INSERT IGNORE INTO nc_message(event_id,receiver_id,msg_type,title,content,biz_type,biz_id) VALUES(?,?,?,?,?,?,?)",event.get("event_id"),event.get("receiver_id"),event.get("event_type"),payload.get("title"),payload.get("content"),payload.get("bizType"),payload.get("bizId"));
                    Long messageId=jdbc.queryForObject("SELECT id FROM nc_message WHERE event_id=? AND receiver_id=?",Long.class,event.get("event_id"),event.get("receiver_id"));
                    jdbc.update("UPDATE sys_outbox SET status='DONE',processed_time=CURRENT_TIMESTAMP WHERE id=?",event.get("id"));
                    return Map.of("receiverId",event.get("receiver_id"),"messageId",messageId.toString(),"type",event.get("event_type"),"title",payload.get("title"));
                } catch(Exception e) {
                    jdbc.update("UPDATE sys_outbox SET retry_count=retry_count+1,status=IF(retry_count>=5,'FAILED','PENDING'),next_attempt_time=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 SECOND),last_error=? WHERE id=?",e.getClass().getSimpleName(),event.get("id"));
                    return Map.of();
                }
            });
            if(result==null) break;
            if(!result.isEmpty()) websocket.push(((Number)result.get("receiverId")).longValue(),JsonUtils.toJsonString(result));
        }
    }
}
