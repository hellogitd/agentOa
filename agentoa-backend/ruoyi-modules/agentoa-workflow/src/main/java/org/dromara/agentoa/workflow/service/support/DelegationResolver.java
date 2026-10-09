package org.dromara.agentoa.workflow.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowDelegate;
import org.dromara.agentoa.workflow.mapper.OaFlowDelegateMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 委托代理解析（P1，需求 WF-12）：受托人在有效期内按 processKeys 获得办理权。
 * <p>
 * 委托只扩展可见与办理范围，不改变任务归属；被委托人办理同样写入审计历史。
 */
@Component
@RequiredArgsConstructor
public class DelegationResolver {

    private final OaFlowDelegateMapper delegateMapper;

    /** 当前用户可代办的委托人账号 ID 列表（有效、启用、流程匹配） */
    public List<Long> delegatedOwnerIds(Long delegateUserId, String processKey) {
        if (delegateUserId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<OaFlowDelegate> delegates = delegateMapper.selectList(new LambdaQueryWrapper<OaFlowDelegate>()
            .eq(OaFlowDelegate::getDelegateId, delegateUserId)
            .eq(OaFlowDelegate::getStatus, 1)
            .le(OaFlowDelegate::getStartDate, today)
            .ge(OaFlowDelegate::getEndDate, today));
        List<Long> owners = new ArrayList<>();
        for (OaFlowDelegate delegate : delegates) {
            if (matchesProcess(delegate, processKey)) {
                owners.add(delegate.getOwnerId());
            }
        }
        return owners;
    }

    /** 任务办理委托判定：办理人是委托人本人且当前用户是其有效受托人 */
    public boolean canActAsDelegate(Long actorUserId, Long assigneeUserId, String processKey) {
        if (actorUserId == null || assigneeUserId == null || actorUserId.equals(assigneeUserId)) {
            return false;
        }
        return delegatedOwnerIds(actorUserId, processKey).contains(assigneeUserId);
    }

    public boolean matchesProcess(OaFlowDelegate delegate, String processKey) {
        String keys = delegate.getProcessKeys();
        if (keys == null || keys.isBlank()) {
            return true;
        }
        if (processKey == null || processKey.isBlank()) {
            return false;
        }
        Set<String> configured = Arrays.stream(keys.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return configured.contains(processKey);
    }
}
