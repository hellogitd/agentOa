package org.dromara.agentoa.config;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service @RequiredArgsConstructor
public class FoundationProbeService {
    private final JdbcTemplate jdbc;
    private final RuntimeService runtime;
    @Transactional public String start(String key, Long userId) {
        jdbc.update("INSERT INTO oa_flow_probe(business_key) VALUES(?)", key);
        String id = runtime.startProcessInstanceByKey("foundationProbe", key, Map.of("assignee", userId.toString())).getId();
        jdbc.update("UPDATE oa_flow_probe SET process_instance_id=? WHERE business_key=?", id, key);
        jdbc.update("INSERT INTO sys_outbox(event_id,receiver_id,event_type,payload) VALUES(?,?,?,?)", key, userId,
            "SYSTEM", "{\"title\":\"基础底座验证\",\"content\":\"业务记录、Flowable 流程与通知事件已在同一事务提交。\"}");
        return id;
    }
}
