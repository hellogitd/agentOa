package org.dromara.agentoa.web;

import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.config.FoundationProbeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.flowable.engine.TaskService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/foundation")
@SaCheckRole("superadmin")
public class FoundationController {
    private final FoundationProbeService probes;
    private final TaskService tasks;
    @GetMapping("/overview") public R<Map<String,Object>> overview() {
        return R.ok(Map.of("application", "AgentOA", "stage", "M0", "workflow", "Flowable 7.2.0",
            "probeTasks", tasks.createTaskQuery().processDefinitionKey("foundationProbe").taskAssignee(LoginHelper.getUserId().toString()).count()));
    }
    @PostMapping("/probes") public R<Map<String,String>> start() {
        String key = UUID.randomUUID().toString();
        return R.ok(Map.of("businessKey",key,"processInstanceId",probes.start(key,LoginHelper.getUserId())));
    }
    @PostMapping("/probes/{processId}/complete") @Transactional public R<Void> complete(@PathVariable String processId) {
        var task=tasks.createTaskQuery().processDefinitionKey("foundationProbe").processInstanceId(processId)
            .taskAssignee(LoginHelper.getUserId().toString()).singleResult();
        if(task==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Task unavailable");
        tasks.complete(task.getId());
        return R.ok();
    }
}
