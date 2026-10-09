package org.dromara.agentoa.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.notice.domain.OaAnnouncement;
import org.dromara.agentoa.notice.domain.OaAnnouncementRead;
import org.dromara.agentoa.notice.domain.bo.AnnouncementBo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.enums.AnnouncementStatus;
import org.dromara.agentoa.notice.domain.vo.AnnouncementVo;
import org.dromara.agentoa.notice.domain.vo.ReadStatusVo;
import org.dromara.agentoa.notice.mapper.NoticeAudienceMapper;
import org.dromara.agentoa.notice.mapper.NoticeIdentityReadMapper;
import org.dromara.agentoa.notice.mapper.NoticePreferenceMapper;
import org.dromara.agentoa.notice.mapper.OaAnnouncementMapper;
import org.dromara.agentoa.notice.mapper.OaAnnouncementReadMapper;
import org.dromara.agentoa.notice.service.IAnnouncementService;
import org.dromara.agentoa.notice.service.support.NoticeOutboxWriter;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公告实现：受众发布时物化，组织变更不追溯；撤回保留受众与已读记录。
 */
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements IAnnouncementService {

    static final String PUBLISH_PATH = "POST /api/v1/notice/{id}/publish";
    private static final String MSG_TYPE_NOTICE = "NOTICE";

    private final OaAnnouncementMapper announcementMapper;
    private final NoticeAudienceMapper audienceMapper;
    private final OaAnnouncementReadMapper readMapper;
    private final NoticeIdentityReadMapper identityMapper;
    private final NoticePreferenceMapper preferenceMapper;
    private final NoticeOutboxWriter outboxWriter;
    private final IdempotencyGuard idempotencyGuard;
    private final org.dromara.agentoa.notice.service.support.NoticeAudienceResolver audienceResolver;

    @Override
    public PageVo<AnnouncementVo> list(NoticePageQuery query, Long actorUserId) {
        LambdaQueryWrapper<OaAnnouncement> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(OaAnnouncement::getStatus, query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaAnnouncement::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByDesc(OaAnnouncement::getIsTop)
            .orderByDesc(OaAnnouncement::getPublishTime)
            .orderByDesc(OaAnnouncement::getCreateTime);
        IPage<OaAnnouncement> result = announcementMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<AnnouncementVo> records = new ArrayList<>();
        for (OaAnnouncement announcement : result.getRecords()) {
            if (visible(announcement, actorUserId)) {
                records.add(toVo(announcement, actorUserId));
            }
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public AnnouncementVo detail(Long id, Long actorUserId) {
        OaAnnouncement announcement = require(id);
        if (!visible(announcement, actorUserId)) {
            throw new ServiceException("NT_NOT_FOUND 公告不存在或无权查看", 404);
        }
        return toVo(announcement, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVo create(AnnouncementBo bo, Long publisherId) {
        OaAnnouncement announcement = new OaAnnouncement();
        apply(announcement, bo);
        announcement.setPublisherId(publisherId);
        announcement.setStatus(AnnouncementStatus.DRAFT.code());
        announcement.setReadCount(0);
        announcement.setCreateTime(new Date());
        announcement.setUpdateTime(new Date());
        announcementMapper.insert(announcement);
        return toVo(announcement, publisherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVo update(Long id, AnnouncementBo bo, Long actorUserId) {
        OaAnnouncement announcement = require(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT.code()) {
            throw new ServiceException("NT_STATE_CONFLICT 已发布内容不可修改，请创建新公告", 409);
        }
        apply(announcement, bo);
        announcement.setUpdateTime(new Date());
        announcementMapper.updateById(announcement);
        return toVo(announcement, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaAnnouncement announcement = require(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT.code()) {
            throw new ServiceException("NT_STATE_CONFLICT 仅草稿可删除", 409);
        }
        announcementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVo publish(Long id, String idempotencyKey, Long publisherId) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key == null || key.isEmpty()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        String replayRef = idempotencyGuard.begin(publisherId, key, PUBLISH_PATH,
            IdempotencyGuard.digest("publish:" + id));
        if (replayRef != null) {
            return detail(Long.valueOf(replayRef), publisherId);
        }
        OaAnnouncement announcement = require(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT.code()) {
            throw new ServiceException("NT_STATE_CONFLICT 公告已发布或已撤回，不可重复发布", 409);
        }
        List<Long> audience = resolveAudience(announcement);
        if (audience.isEmpty()) {
            throw new ServiceException("NT_AUDIENCE_EMPTY 公告受众为空", 400);
        }
        Date now = new Date();
        for (Long userId : audience) {
            audienceMapper.insert(announcement.getId(), userId, now);
        }
        announcement.setStatus(AnnouncementStatus.PUBLISHED.code());
        announcement.setPublishTime(now);
        announcement.setReadCount(0);
        announcement.setUpdateTime(now);
        announcementMapper.updateById(announcement);
        fanOut(announcement, audience);
        idempotencyGuard.complete(publisherId, key, String.valueOf(announcement.getId()));
        return toVo(announcement, publisherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recall(Long id, Long actorUserId) {
        OaAnnouncement announcement = require(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED.code()) {
            throw new ServiceException("NT_STATE_CONFLICT 仅已发布可撤回", 409);
        }
        announcement.setStatus(AnnouncementStatus.RECALLED.code());
        announcement.setUpdateTime(new Date());
        announcementMapper.updateById(announcement);
    }

    @Override
    public ReadStatusVo readStatus(Long id, Long actorUserId) {
        OaAnnouncement announcement = require(id);
        ReadStatusVo vo = new ReadStatusVo();
        vo.setNoticeId(id);
        List<Long> audience = audienceMapper.selectUserIds(id);
        List<OaAnnouncementRead> reads = readMapper.selectList(new LambdaQueryWrapper<OaAnnouncementRead>()
            .eq(OaAnnouncementRead::getNoticeId, id));
        Set<Long> readUserIds = new LinkedHashSet<>();
        List<ReadStatusVo.UserBrief> readUsers = new ArrayList<>();
        for (OaAnnouncementRead read : reads) {
            readUserIds.add(read.getUserId());
            ReadStatusVo.UserBrief brief = new ReadStatusVo.UserBrief();
            brief.setUserId(read.getUserId());
            brief.setNickname(identityMapper.selectNickName(read.getUserId()));
            brief.setReadTime(String.valueOf(read.getReadTime()));
            readUsers.add(brief);
        }
        List<ReadStatusVo.UserBrief> unreadUsers = new ArrayList<>();
        for (Long userId : audience) {
            if (!readUserIds.contains(userId)) {
                ReadStatusVo.UserBrief brief = new ReadStatusVo.UserBrief();
                brief.setUserId(userId);
                brief.setNickname(identityMapper.selectNickName(userId));
                unreadUsers.add(brief);
            }
        }
        vo.setAudienceCount(audience.size());
        vo.setReadCount(readUsers.size());
        vo.setReadUsers(readUsers);
        vo.setUnreadUsers(unreadUsers);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, Long userId) {
        OaAnnouncement announcement = require(id);
        if (!visible(announcement, userId)) {
            throw new ServiceException("NT_NOT_FOUND 公告不存在或无权查看", 404);
        }
        OaAnnouncementRead read = new OaAnnouncementRead();
        read.setNoticeId(id);
        read.setUserId(userId);
        read.setReadTime(new Date());
        try {
            readMapper.insert(read);
        } catch (DuplicateKeyException e) {
            return;
        }
        announcementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OaAnnouncement>()
            .eq(OaAnnouncement::getId, id)
            .setSql("read_count = read_count + 1"));
    }

    // ------------------------------------------------------------ internal

    /** 可见性：管理者/发布人全部，其他人在受众快照内的已发布公告（docs/15 权限规则） */
    private boolean visible(OaAnnouncement announcement, Long actorUserId) {
        boolean inAudience = audienceMapper.exists(announcement.getId(), actorUserId) > 0;
        boolean published = announcement.getStatus() == AnnouncementStatus.PUBLISHED.code();
        return org.dromara.agentoa.notice.domain.policy.NoticeAccessPolicy.canViewNotice(
            org.dromara.common.satoken.utils.LoginHelper.isSuperAdmin(), permissions(), actorUserId,
            announcement.getPublisherId(), inAudience, published);
    }

    private List<Long> resolveAudience(OaAnnouncement announcement) {
        return audienceResolver.resolve(announcement.getScopeType(), announcement.getScopeValues());
    }

    /** 事务内写 outbox（与公告状态同事务），投递器在提交后落 nc_message 并推 WebSocket */
    private void fanOut(OaAnnouncement announcement, List<Long> audience) {
        String preview = announcement.getContent() == null ? "" : announcement.getContent();
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }
        for (Long userId : audience) {
            if (!pushEnabled(userId)) {
                continue;
            }
            outboxWriter.writeNotice("NOTICE-" + announcement.getId() + "-" + userId,
                userId, announcement.getTitle(), preview, announcement.getId());
        }
    }

    /** 未配置偏好默认提醒 */
    private boolean pushEnabled(Long userId) {
        Integer enabled = preferenceMapper.selectEnabled(userId, MSG_TYPE_NOTICE);
        return enabled == null || enabled != 0;
    }

    private void apply(OaAnnouncement announcement, AnnouncementBo bo) {
        announcement.setTitle(bo.getTitle().trim());
        announcement.setContent(bo.getContent());
        announcement.setNoticeType(bo.getNoticeType());
        announcement.setScopeType(Integer.valueOf(bo.getScopeType()));
        announcement.setScopeValues(bo.getScopeValues());
        announcement.setIsTop(bo.getIsTop() == null ? 0 : bo.getIsTop());
        announcement.setIsPopup(bo.getIsPopup() == null ? 0 : bo.getIsPopup());
        announcement.setAttachments(bo.getAttachments());
        announcement.setRemark(bo.getRemark());
    }

    private OaAnnouncement require(Long id) {
        OaAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new ServiceException("NT_NOT_FOUND 公告不存在", 404);
        }
        return announcement;
    }

    private AnnouncementVo toVo(OaAnnouncement announcement, Long actorUserId) {
        AnnouncementVo vo = new AnnouncementVo();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setNoticeType(announcement.getNoticeType());
        vo.setPublisherId(announcement.getPublisherId());
        vo.setPublisherName(identityMapper.selectNickName(announcement.getPublisherId()));
        vo.setPublishTime(String.valueOf(announcement.getPublishTime()));
        vo.setEffectiveStart(announcement.getEffectiveStart() == null ? null : String.valueOf(announcement.getEffectiveStart()));
        vo.setEffectiveEnd(announcement.getEffectiveEnd() == null ? null : String.valueOf(announcement.getEffectiveEnd()));
        vo.setScopeType(announcement.getScopeType());
        vo.setScopeValues(announcement.getScopeValues());
        vo.setIsTop(announcement.getIsTop());
        vo.setIsPopup(announcement.getIsPopup());
        vo.setStatus(announcement.getStatus());
        vo.setReadCount(announcement.getReadCount());
        vo.setAudienceCount((int) audienceMapper.count(announcement.getId()));
        vo.setAttachments(announcement.getAttachments());
        vo.setRead(readMapper.selectCount(new LambdaQueryWrapper<OaAnnouncementRead>()
            .eq(OaAnnouncementRead::getNoticeId, announcement.getId())
            .eq(OaAnnouncementRead::getUserId, actorUserId)) > 0);
        vo.setCreateTime(String.valueOf(announcement.getCreateTime()));
        vo.setUpdateTime(announcement.getUpdateTime() == null ? null : String.valueOf(announcement.getUpdateTime()));
        return vo;
    }

    private Set<String> permissions() {
        var loginUser = org.dromara.common.satoken.utils.LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getMenuPermission() == null ? Set.of() : loginUser.getMenuPermission();
    }
}
