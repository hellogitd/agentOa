package org.dromara.agentoa.ai.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.notice.domain.OaAnnouncement;
import org.dromara.agentoa.notice.domain.OaNoticeTemplate;
import org.dromara.agentoa.notice.domain.policy.NoticeAccessPolicy;
import org.dromara.agentoa.notice.mapper.OaAnnouncementMapper;
import org.dromara.agentoa.notice.mapper.OaNoticeTemplateMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 通知/公文起草只读上下文（docs/21 AI-M4-03）。
 * <p>
 * 仅具备发布通知权限的账号可用；草稿/模板只读，产出为草稿文本，人工确认后才可发布。
 */
@Component
@RequiredArgsConstructor
public class NoticeCopilotReader implements CopilotBizReader {

    private static final int MAX_CONTENT_CHARS = 4000;

    private final OaAnnouncementMapper announcementMapper;
    private final OaNoticeTemplateMapper noticeTemplateMapper;

    @Override
    public String scene() {
        return AiCopilotScene.NOTICE_DRAFT.code();
    }

    @Override
    public String readContext(Long bizId, Long userId) {
        Set<String> permissions = LoginHelper.getLoginUser() == null
            ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        if (!NoticeAccessPolicy.canManage(LoginHelper.isSuperAdmin(), permissions,
            NoticeAccessPolicy.PERM_NOTICE_ADD)) {
            throw new ServiceException("AI_COPILOT_BIZ_FORBIDDEN 无权起草通知", 403);
        }
        if (bizId == null) {
            return "";
        }
        OaAnnouncement announcement = announcementMapper.selectById(bizId);
        if (announcement != null) {
            return "参考通知（" + statusLabel(announcement.getStatus()) + "）："
                + nullToEmpty(announcement.getTitle()) + "\n"
                + truncate(nullToEmpty(announcement.getContent()));
        }
        OaNoticeTemplate template = noticeTemplateMapper.selectById(bizId);
        if (template != null) {
            return "参考模板：" + nullToEmpty(template.getName()) + "\n"
                + "标题模板：" + nullToEmpty(template.getTitleTpl()) + "\n"
                + "正文模板：" + truncate(nullToEmpty(template.getContentTpl()));
        }
        throw new ServiceException("AI_COPILOT_BIZ_NOT_FOUND 通知或模板不存在", 404);
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "草稿";
            case 2 -> "已发布";
            case 3 -> "已撤回";
            case 4 -> "已归档";
            default -> "状态" + status;
        };
    }

    private static String truncate(String content) {
        return content.length() <= MAX_CONTENT_CHARS ? content : content.substring(0, MAX_CONTENT_CHARS) + "…";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
