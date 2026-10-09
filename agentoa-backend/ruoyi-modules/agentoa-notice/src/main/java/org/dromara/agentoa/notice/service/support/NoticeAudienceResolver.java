package org.dromara.agentoa.notice.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.notice.domain.enums.ScopeType;
import org.dromara.agentoa.notice.mapper.NoticeIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 受众解析（docs/15 第 3 步）：ALL/DEPT/ROLE/USER 四类，公告发布与模板推送/发送同源复用。
 */
@Component
@RequiredArgsConstructor
public class NoticeAudienceResolver {

    private final NoticeIdentityReadMapper identity;

    /** 按 scope 解析受众（去重保序）；范围值非法 400 */
    public List<Long> resolve(int scopeType, String scopeValues) {
        ScopeType scope;
        try {
            scope = ScopeType.from(scopeType);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("NT_SCOPE_INVALID 受众范围取值非法: " + scopeType, 400);
        }
        List<Long> users = switch (scope) {
            case ALL -> identity.selectActiveUserIds();
            case DEPT -> {
                List<Long> deptIds = parseIds(scopeValues);
                yield deptIds.isEmpty() ? List.of() : identity.selectUserIdsByDeptIds(deptIds);
            }
            case ROLE -> {
                List<String> roleKeys = parseStrings(scopeValues);
                yield roleKeys.isEmpty() ? List.of() : identity.selectUserIdsByRoleKeys(roleKeys);
            }
            case USER -> {
                List<Long> userIds = parseIds(scopeValues);
                yield userIds.isEmpty() ? List.of() : identity.selectExistingUserIds(userIds);
            }
        };
        return new ArrayList<>(new LinkedHashSet<>(users));
    }

    /** 逗号分隔的 ID 串；非法数字 400 */
    public List<Long> parseIds(String values) {
        List<Long> ids = new ArrayList<>();
        if (values == null || values.isBlank()) {
            return ids;
        }
        for (String token : values.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                throw new ServiceException("NT_SCOPE_INVALID 受众范围值非法: " + trimmed, 400);
            }
        }
        return ids;
    }

    /** 逗号分隔的 key 串 */
    public List<String> parseStrings(String values) {
        List<String> keys = new ArrayList<>();
        if (values == null || values.isBlank()) {
            return keys;
        }
        for (String token : values.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed);
            }
        }
        return keys;
    }
}
