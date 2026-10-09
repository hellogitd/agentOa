package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.common.json.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识域可见性判定（docs/21 AI-M3-01/07）：
 * 可见 = 全员可见 ∪ 创建者 ∪ 显式成员；写操作仅创建者（对象权限模式，同知识库 OWNER）。
 */
public final class AiKbVisibility {

    private AiKbVisibility() {
    }

    public static boolean visible(Long userId, OaAiKb kb) {
        if (userId == null || kb == null) {
            return false;
        }
        if (OaAiKb.STATUS_DISABLED.equals(kb.getStatus())) {
            return false;
        }
        if (OaAiKb.VISIBILITY_ALL.equals(kb.getVisibility())) {
            return true;
        }
        if (userId.equals(kb.getCreateBy())) {
            return true;
        }
        return memberIds(kb).contains(userId);
    }

    public static boolean owner(Long userId, OaAiKb kb) {
        return userId != null && kb != null && userId.equals(kb.getCreateBy());
    }

    public static List<Long> memberIds(OaAiKb kb) {
        return parseMembers(kb == null ? null : kb.getMemberScope());
    }

    public static List<Long> parseMembers(String memberScopeJson) {
        if (memberScopeJson == null || memberScopeJson.isBlank()) {
            return new ArrayList<>();
        }
        List<Long> ids = JsonUtils.parseArray(memberScopeJson, Long.class);
        return ids == null ? new ArrayList<>() : ids;
    }

    public static String formatMembers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        List<Long> unique = userIds.stream().distinct().toList();
        return JsonUtils.toJsonString(unique);
    }
}
