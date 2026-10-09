package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.domain.OaAiQuota;
import org.dromara.agentoa.ai.domain.OaAiUsageLog;
import org.dromara.agentoa.ai.mapper.AiIdentityMapper;
import org.dromara.agentoa.ai.mapper.OaAiQuotaMapper;
import org.dromara.agentoa.ai.mapper.OaAiUsageLogMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用量配额判定（docs/21 AI-M1-08）：网关入口判定，超限 429 {@code AI_QUOTA_EXCEEDED}。
 * <p>
 * 未配置配额的主体走全局兜底（默认日 10 万 token / 500 次，可配置）。
 */
@Component
public class AiQuotaGuard {

    private final OaAiQuotaMapper quotaMapper;
    private final OaAiUsageLogMapper usageLogMapper;
    private final AiIdentityMapper identityMapper;
    private final long fallbackTokenLimit;
    private final int fallbackRequestLimit;

    @Autowired
    public AiQuotaGuard(OaAiQuotaMapper quotaMapper,
                        OaAiUsageLogMapper usageLogMapper,
                        AiIdentityMapper identityMapper,
                        @Value("${agentoa.ai.quota.fallback-token-limit:100000}") long fallbackTokenLimit,
                        @Value("${agentoa.ai.quota.fallback-request-limit:500}") int fallbackRequestLimit) {
        this.quotaMapper = quotaMapper;
        this.usageLogMapper = usageLogMapper;
        this.identityMapper = identityMapper;
        this.fallbackTokenLimit = fallbackTokenLimit;
        this.fallbackRequestLimit = fallbackRequestLimit;
    }

    /**
     * 调用前判定。
     *
     * @param pendingTokens 本次预估 token（输入 + 预留输出）
     */
    public void checkBeforeCall(Long userId, long pendingTokens) {
        if (userId == null) {
            return;
        }
        List<Long> roleIds = identityMapper.selectRoleIds(userId);
        List<OaAiQuota> quotas = loadQuotas(userId, roleIds);
        if (quotas.isEmpty()) {
            long tokens = usedTokens(List.of(userId), periodStart(OaAiQuota.PERIOD_DAY));
            long requests = usedRequests(List.of(userId), periodStart(OaAiQuota.PERIOD_DAY));
            if (fallbackTokenLimit > 0 && tokens + pendingTokens > fallbackTokenLimit) {
                throw quotaExceeded("日 token 配额已用尽");
            }
            if (fallbackRequestLimit > 0 && requests + 1 > fallbackRequestLimit) {
                throw quotaExceeded("日请求次数配额已用尽");
            }
            return;
        }
        for (OaAiQuota quota : quotas) {
            Date since = periodStart(quota.getPeriodType());
            List<Long> subjects = subjectUserIds(quota, userId, roleIds);
            if (quota.getTokenLimit() != null && quota.getTokenLimit() > 0) {
                long tokens = usedTokens(subjects, since);
                if (tokens + pendingTokens > quota.getTokenLimit()) {
                    throw quotaExceeded("token 配额已用尽（" + quota.getScopeName() + "）");
                }
            }
            if (quota.getRequestLimit() != null && quota.getRequestLimit() > 0) {
                long requests = usedRequests(subjects, since);
                if (requests + 1 > quota.getRequestLimit()) {
                    throw quotaExceeded("请求次数配额已用尽（" + quota.getScopeName() + "）");
                }
            }
        }
    }

    /**
     * 流式中途判定（docs/21 §4.4「限额中途超限中断提示」）。
     *
     * @param consumedTokens 本次调用累计消耗 token（输入 + 已产出），按累计用量二次校验；
     *                       超限抛 429 {@code AI_QUOTA_EXCEEDED}，由网关熔断流式输出
     */
    public void checkMidStream(Long userId, long consumedTokens) {
        checkBeforeCall(userId, Math.max(0, consumedTokens));
    }

    private List<OaAiQuota> loadQuotas(Long userId, List<Long> roleIds) {
        List<OaAiQuota> quotas = quotaMapper.selectList(new LambdaQueryWrapper<OaAiQuota>()
            .eq(OaAiQuota::getEnabled, 1)
            .and(wrapper -> wrapper
                .eq(OaAiQuota::getScopeType, OaAiQuota.SCOPE_USER).eq(OaAiQuota::getScopeId, userId)
                .or(inner -> inner.eq(OaAiQuota::getScopeType, OaAiQuota.SCOPE_ROLE)
                    .in(OaAiQuota::getScopeId,
                        roleIds == null || roleIds.isEmpty() ? List.of(-1L) : roleIds))));
        return quotas == null ? List.of() : quotas;
    }

    private List<Long> subjectUserIds(OaAiQuota quota, Long userId, List<Long> roleIds) {
        if (OaAiQuota.SCOPE_USER.equals(quota.getScopeType())) {
            return List.of(quota.getScopeId());
        }
        Set<Long> users = new LinkedHashSet<>(identityMapper.selectUserIdsByRole(quota.getScopeId()));
        users.add(userId);
        return new ArrayList<>(users);
    }

    private long usedTokens(List<Long> userIds, Date since) {
        List<OaAiUsageLog> rows = usageRows(userIds, since);
        long total = 0L;
        for (OaAiUsageLog row : rows) {
            total += row.getTotalTokens() == null ? 0 : row.getTotalTokens();
        }
        return total;
    }

    private long usedRequests(List<Long> userIds, Date since) {
        return usageRows(userIds, since).size();
    }

    private List<OaAiUsageLog> usageRows(List<Long> userIds, Date since) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return usageLogMapper.selectList(new LambdaQueryWrapper<OaAiUsageLog>()
            .select(OaAiUsageLog::getTotalTokens)
            .in(OaAiUsageLog::getUserId, userIds)
            .ge(OaAiUsageLog::getCreateTime, since));
    }

    private Date periodStart(String periodType) {
        LocalDate today = LocalDate.now();
        LocalDate start = OaAiQuota.PERIOD_MONTH.equals(periodType) ? today.withDayOfMonth(1) : today;
        return Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private ServiceException quotaExceeded(String message) {
        return new ServiceException("AI_QUOTA_EXCEEDED " + message, 429);
    }
}
