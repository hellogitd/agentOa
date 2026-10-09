package org.dromara.agentoa.workflow.assigner;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 办理人解析器（docs/12）：支持 SELF / LEADER / DEPT_HEAD / ROLE:key / WHITELIST:id,id / USER_SELECT。
 * 只做白名单规则查询，禁止 SpEL、Bean 调用和用户脚本。
 * <p>
 * {@code USER_SELECT}（发起人自选）：从流程变量 {@code userSelect_<nodeId>} 读取发起人在发起页
 * 选定的办理人（Long 或 List&lt;Long&gt;）；缺失或为空时确定性报错，不回退任意人。
 */
@Component
@RequiredArgsConstructor
public class AssigneeResolver {

    /** 发起人自选规则值 */
    public static final String RULE_USER_SELECT = "USER_SELECT";

    /** 自选办理人流程变量前缀：userSelect_<nodeId> */
    public static final String USER_SELECT_VARIABLE_PREFIX = "userSelect_";

    private final WorkflowIdentityReadMapper identityMapper;

    public AssigneeSet resolve(String rule, Long initiatorUserId, Long initiatorDeptId) {
        return resolve(rule, initiatorUserId, initiatorDeptId, null);
    }

    /**
     * @param userSelection USER_SELECT 规则的发起人选择结果（Long / List&lt;Long&gt; / 数字字符串）
     */
    public AssigneeSet resolve(String rule, Long initiatorUserId, Long initiatorDeptId, Object userSelection) {
        if (rule == null || rule.isBlank()) {
            throw new AssigneeResolutionException("办理人规则不能为空");
        }
        String normalized = rule.trim();
        if (RULE_USER_SELECT.equals(normalized)) {
            return resolveUserSelect(userSelection);
        }
        if (normalized.startsWith("ROLE:")) {
            String roleKey = normalized.substring("ROLE:".length()).trim();
            List<Long> users = identityMapper.selectUserIdsByRoleKey(roleKey);
            if (users == null || users.isEmpty()) {
                throw new AssigneeResolutionException("角色 " + roleKey + " 没有可用办理人");
            }
            return AssigneeSet.candidates(users);
        }
        if (normalized.startsWith("WHITELIST:")) {
            List<Long> users = new ArrayList<>();
            for (String part : normalized.substring("WHITELIST:".length()).split(",")) {
                if (!part.isBlank()) {
                    users.add(Long.valueOf(part.trim()));
                }
            }
            if (users.isEmpty()) {
                throw new AssigneeResolutionException("白名单办理人为空");
            }
            return AssigneeSet.candidates(users);
        }
        Long userId = switch (normalized) {
            case "SELF" -> initiatorUserId;
            case "LEADER" -> resolveLeader(initiatorUserId, initiatorDeptId);
            case "DEPT_HEAD" -> resolveDeptHead(initiatorUserId, initiatorDeptId);
            default -> throw new AssigneeResolutionException("未知办理人规则: " + normalized);
        };
        if (userId == null) {
            throw new AssigneeResolutionException("办理人规则 " + normalized + " 解析不到办理人");
        }
        return AssigneeSet.single(userId);
    }

    /**
     * 发起人自选：缺失/为空确定性报错（400 回滚），单人返回 assignee、多人返回候选集。
     */
    public AssigneeSet resolveUserSelect(Object userSelection) {
        List<Long> users = normalizeSelection(userSelection);
        if (users.isEmpty()) {
            throw new AssigneeResolutionException("发起人自选办理人缺失");
        }
        if (users.size() == 1) {
            return AssigneeSet.single(users.get(0));
        }
        return AssigneeSet.candidates(users);
    }

    /** 自选结果归一化：Long / List / Collection / 数字字符串 → 去重 Long 列表 */
    public static List<Long> normalizeSelection(Object userSelection) {
        List<Long> users = new ArrayList<>();
        if (userSelection == null) {
            return users;
        }
        if (userSelection instanceof Collection<?> collection) {
            for (Object item : collection) {
                Long userId = toUserId(item);
                if (userId != null && !users.contains(userId)) {
                    users.add(userId);
                }
            }
            return users;
        }
        Long userId = toUserId(userSelection);
        if (userId != null) {
            users.add(userId);
        }
        return users;
    }

    private static Long toUserId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private Long resolveLeader(Long initiatorUserId, Long initiatorDeptId) {
        if (initiatorUserId == null) {
            return null;
        }
        Long leader = identityMapper.selectDirectLeaderUserId(initiatorUserId);
        if (leader != null) {
            return leader;
        }
        return resolveDeptHead(initiatorUserId, initiatorDeptId);
    }

    private Long resolveDeptHead(Long initiatorUserId, Long initiatorDeptId) {
        Long deptId = initiatorDeptId;
        if (deptId == null && initiatorUserId != null) {
            deptId = identityMapper.selectEmployeeDeptId(initiatorUserId);
            if (deptId == null) {
                deptId = identityMapper.selectUserDeptId(initiatorUserId);
            }
        }
        return deptId == null ? null : identityMapper.selectDeptLeaderUserId(deptId);
    }

    public String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        String nick = identityMapper.selectNickName(userId);
        return nick != null ? nick : identityMapper.selectUserName(userId);
    }

    public record AssigneeSet(Long assigneeUserId, List<Long> candidateUserIds) {

        public static AssigneeSet single(Long userId) {
            return new AssigneeSet(userId, List.of());
        }

        public static AssigneeSet candidates(List<Long> users) {
            return new AssigneeSet(null, List.copyOf(users));
        }

        public boolean isEmpty() {
            return assigneeUserId == null && (candidateUserIds == null || candidateUserIds.isEmpty());
        }

        public List<Long> allUserIds() {
            if (assigneeUserId != null) {
                return List.of(assigneeUserId);
            }
            return candidateUserIds == null ? List.of() : candidateUserIds;
        }
    }
}
