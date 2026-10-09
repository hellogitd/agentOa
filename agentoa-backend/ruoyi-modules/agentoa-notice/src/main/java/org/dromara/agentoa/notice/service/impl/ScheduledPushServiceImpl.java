package org.dromara.agentoa.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.OaAnnouncement;
import org.dromara.agentoa.notice.domain.OaNoticeTemplate;
import org.dromara.agentoa.notice.domain.OaScheduledPush;
import org.dromara.agentoa.notice.domain.OaScheduledPushRun;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.ScheduledPushBo;
import org.dromara.agentoa.notice.domain.enums.PushType;
import org.dromara.agentoa.notice.domain.enums.ScheduleType;
import org.dromara.agentoa.notice.domain.vo.AnnouncementVo;
import org.dromara.agentoa.notice.domain.vo.PushRunVo;
import org.dromara.agentoa.notice.domain.vo.ScheduledPushVo;
import org.dromara.agentoa.notice.mapper.NoticePreferenceMapper;
import org.dromara.agentoa.notice.mapper.OaAnnouncementMapper;
import org.dromara.agentoa.notice.mapper.OaNoticeTemplateMapper;
import org.dromara.agentoa.notice.mapper.OaScheduledPushMapper;
import org.dromara.agentoa.notice.mapper.OaScheduledPushRunMapper;
import org.dromara.agentoa.notice.service.IAnnouncementService;
import org.dromara.agentoa.notice.service.IScheduledPushService;
import org.dromara.agentoa.notice.service.support.NoticeAudienceResolver;
import org.dromara.agentoa.notice.service.support.NoticeOutboxWriter;
import org.dromara.agentoa.notice.service.support.NoticeTemplateRenderer;
import org.dromara.agentoa.notice.service.support.PushCron;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时推送实现（NC-04）：DB 租约（FOR UPDATE SKIP LOCKED）+ event_key 幂等去重。
 * <p>
 * 执行与 next_run_time 滚动同事务；失败单独落 FAILED 执行记录并推进 slot，重扫不重复发。
 * 定时公告复用 AnnouncementService.publish（受众物化+outbox 同事务），模板推送按受众渲染写 outbox。
 */
@Service
@RequiredArgsConstructor
public class ScheduledPushServiceImpl implements IScheduledPushService {

    private static final int MAX_BATCH = 10;
    private static final int ERROR_MAX = 500;

    private final OaScheduledPushMapper pushMapper;
    private final OaScheduledPushRunMapper runMapper;
    private final OaNoticeTemplateMapper templateMapper;
    private final OaAnnouncementMapper announcementMapper;
    private final IAnnouncementService announcementService;
    private final NoticeAudienceResolver audienceResolver;
    private final NoticeOutboxWriter outboxWriter;
    private final NoticePreferenceMapper preferenceMapper;
    private final TransactionTemplate transactions;

    @Override
    public PageVo<ScheduledPushVo> list(NoticePageQuery query) {
        LambdaQueryWrapper<OaScheduledPush> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaScheduledPush::getName, query.getKeyword().trim());
        }
        wrapper.orderByDesc(OaScheduledPush::getId);
        IPage<OaScheduledPush> result = pushMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        return PageVo.of(result.getRecords().stream().map(this::toVo).toList(),
            result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public ScheduledPushVo detail(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledPushVo create(ScheduledPushBo bo, Long operatorUserId) {
        OaScheduledPush push = new OaScheduledPush();
        apply(push, bo);
        push.setStatus(bo.getStatus() == null ? OaScheduledPush.STATUS_ENABLED : Integer.valueOf(bo.getStatus()));
        push.setRunCount(0);
        push.setCreateBy(operatorUserId);
        push.setCreateTime(LocalDateTime.now());
        pushMapper.insert(push);
        return toVo(push);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledPushVo update(Long id, ScheduledPushBo bo, Long operatorUserId) {
        OaScheduledPush push = require(id);
        apply(push, bo);
        push.setUpdateBy(operatorUserId);
        push.setUpdateTime(LocalDateTime.now());
        pushMapper.updateById(push);
        return toVo(push);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        pushMapper.deleteById(id);
        runMapper.delete(new LambdaQueryWrapper<OaScheduledPushRun>().eq(OaScheduledPushRun::getPushId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id) {
        OaScheduledPush push = require(id);
        pushMapper.update(null, new LambdaUpdateWrapper<OaScheduledPush>()
            .eq(OaScheduledPush::getId, push.getId())
            .set(OaScheduledPush::getStatus, OaScheduledPush.STATUS_PAUSED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        OaScheduledPush push = require(id);
        LocalDateTime next = push.getNextRunTime();
        if (next == null) {
            next = initialNextRunTime(push);
        }
        pushMapper.update(null, new LambdaUpdateWrapper<OaScheduledPush>()
            .eq(OaScheduledPush::getId, push.getId())
            .set(OaScheduledPush::getStatus, OaScheduledPush.STATUS_ENABLED)
            .set(OaScheduledPush::getNextRunTime, next));
    }

    @Override
    public PageVo<PushRunVo> runs(Long pushId, NoticePageQuery query) {
        LambdaQueryWrapper<OaScheduledPushRun> wrapper = new LambdaQueryWrapper<OaScheduledPushRun>()
            .eq(OaScheduledPushRun::getPushId, pushId)
            .orderByDesc(OaScheduledPushRun::getSlotTime)
            .orderByDesc(OaScheduledPushRun::getId);
        IPage<OaScheduledPushRun> result = runMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<PushRunVo> records = result.getRecords().stream().map(this::toRunVo).toList();
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public int executeDue() {
        int executed = 0;
        for (int n = 0; n < MAX_BATCH; n++) {
            Object[] claimed = new Object[2];
            Boolean result;
            try {
                result = transactions.execute(status -> executeOneDue(claimed));
            } catch (RuntimeException e) {
                if (claimed[0] != null) {
                    try {
                        recordFailure((Long) claimed[0], (LocalDateTime) claimed[1], e);
                    } catch (RuntimeException ignored) {
                        // 失败记录自身异常：slot 未消费，下一 tick 自愈重试
                    }
                }
                continue;
            }
            if (result == null) {
                break;
            }
            if (Boolean.TRUE.equals(result)) {
                executed++;
            }
        }
        return executed;
    }

    // ------------------------------------------------------------ execution

    /** 抢占并执行一个到期任务：成功=true、slot 被消费跳过=false、无到期=null */
    private Boolean executeOneDue(Object[] claimed) {
        List<Map<String, Object>> rows = pushMapper.claimDue();
        if (rows.isEmpty()) {
            return null;
        }
        Long pushId = ((Number) rows.get(0).get("id")).longValue();
        OaScheduledPush push = pushMapper.selectById(pushId);
        if (push == null || push.getStatus() != OaScheduledPush.STATUS_ENABLED || push.getNextRunTime() == null) {
            return false;
        }
        LocalDateTime slot = push.getNextRunTime();
        claimed[0] = pushId;
        claimed[1] = slot;
        String eventKey = eventKey(pushId, slot);
        OaScheduledPushRun run = new OaScheduledPushRun();
        run.setPushId(pushId);
        run.setEventKey(eventKey);
        run.setSlotTime(slot);
        run.setStatus(OaScheduledPushRun.STATUS_SUCCESS);
        run.setReceiverCount(0);
        run.setCreateTime(LocalDateTime.now());
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException e) {
            advance(push, slot, null);
            return false;
        }
        int receivers = executePush(push, eventKey);
        runMapper.update(null, new LambdaUpdateWrapper<OaScheduledPushRun>()
            .eq(OaScheduledPushRun::getId, run.getId())
            .set(OaScheduledPushRun::getReceiverCount, receivers));
        advance(push, slot, null);
        return true;
    }

    private int executePush(OaScheduledPush push, String eventKey) {
        PushType type = PushType.from(push.getPushType());
        if (type == PushType.ANNOUNCEMENT) {
            AnnouncementVo published = announcementService.publish(push.getAnnouncementId(), eventKey,
                push.getCreateBy() == null ? 0L : push.getCreateBy());
            return published.getAudienceCount() == null ? 0 : published.getAudienceCount();
        }
        OaNoticeTemplate template = templateMapper.selectById(push.getTemplateId());
        if (template == null) {
            throw new ServiceException("NT_TPL_NOT_FOUND 推送模板不存在", 404);
        }
        if (template.getStatus() != OaNoticeTemplate.STATUS_ENABLED) {
            throw new ServiceException("NT_TPL_DISABLED 推送模板已停用", 409);
        }
        return fanOutTemplate(push, template, eventKey);
    }

    /** 模板推送：按受众渲染写 outbox（与业务同事务，失败事件走既有 FAILED 重投通道） */
    private int fanOutTemplate(OaScheduledPush push, OaNoticeTemplate template, String eventKey) {
        Set<String> declared = NoticeTemplateRenderer.checkDeclared(
            template.getTitleTpl(), template.getContentTpl(),
            NoticeTemplateServiceImpl.parseVars(template.getVarsJson()));
        Map<String, String> values = parseVarValues(push.getVarsJson());
        String title = NoticeTemplateRenderer.render(template.getTitleTpl(), declared, values);
        String content = NoticeTemplateRenderer.render(template.getContentTpl(), declared, values);
        List<Long> audience = audienceResolver.resolve(push.getScopeType(), push.getScopeValues());
        if (audience.isEmpty()) {
            throw new ServiceException("NT_AUDIENCE_EMPTY 推送受众为空", 400);
        }
        int delivered = 0;
        for (Long userId : audience) {
            if (!pushEnabled(userId, template.getMsgType())) {
                continue;
            }
            outboxWriter.write(eventKey + "-" + userId, userId, template.getMsgType(),
                title, content, "template", template.getId(), "/notice/message");
            delivered++;
        }
        return delivered;
    }

    /** 执行失败：单独事务落 FAILED 执行记录并推进 slot，保证重扫不重复、不卡死调度 */
    private void recordFailure(Long pushId, LocalDateTime slot, RuntimeException error) {
        transactions.execute(status -> {
            OaScheduledPushRun run = new OaScheduledPushRun();
            run.setPushId(pushId);
            run.setEventKey(eventKey(pushId, slot));
            run.setSlotTime(slot);
            run.setStatus(OaScheduledPushRun.STATUS_FAILED);
            run.setReceiverCount(0);
            run.setError(truncate(error));
            run.setCreateTime(LocalDateTime.now());
            try {
                runMapper.insert(run);
            } catch (DuplicateKeyException e) {
                return null;
            }
            OaScheduledPush push = pushMapper.selectById(pushId);
            if (push != null) {
                advance(push, slot, truncate(error));
            }
            return null;
        });
    }

    /** 滚动 next_run_time：单次置 NULL，周期取 max(slot, now) 之后的下一个 slot（漏发合并、不回放）；成功累计 run_count */
    private void advance(OaScheduledPush push, LocalDateTime slot, String error) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = null;
        if (push.getScheduleType() != null && push.getScheduleType() == ScheduleType.CRON.code()) {
            LocalDateTime base = slot.isAfter(now) ? slot : now;
            next = PushCron.parse(push.getCronExpr()).nextAfter(base);
        }
        LambdaUpdateWrapper<OaScheduledPush> update = new LambdaUpdateWrapper<OaScheduledPush>()
            .eq(OaScheduledPush::getId, push.getId())
            .set(OaScheduledPush::getNextRunTime, next)
            .set(OaScheduledPush::getLastRunTime, now)
            .set(OaScheduledPush::getLastError, error);
        if (error == null) {
            update.setSql("run_count = run_count + 1");
        }
        pushMapper.update(null, update);
    }

    private LocalDateTime initialNextRunTime(OaScheduledPush push) {
        if (push.getScheduleType() != null && push.getScheduleType() == ScheduleType.CRON.code()) {
            return PushCron.parse(push.getCronExpr()).nextAfter(LocalDateTime.now());
        }
        return push.getRunAt();
    }

    // ------------------------------------------------------------ validation & mapping

    private void apply(OaScheduledPush push, ScheduledPushBo bo) {
        PushType pushType = PushType.from(Integer.parseInt(bo.getPushType()));
        ScheduleType scheduleType = ScheduleType.from(Integer.parseInt(bo.getScheduleType()));
        push.setName(bo.getName().trim());
        push.setPushType(pushType.code());
        push.setScheduleType(scheduleType.code());
        push.setRemark(bo.getRemark());
        if (pushType == PushType.ANNOUNCEMENT) {
            if (bo.getAnnouncementId() == null) {
                throw new ServiceException("NT_PUSH_INVALID 定时公告必须关联草稿公告", 400);
            }
            OaAnnouncement announcement = announcementMapper.selectById(bo.getAnnouncementId());
            if (announcement == null) {
                throw new ServiceException("NT_PUSH_INVALID 关联公告不存在", 400);
            }
            push.setAnnouncementId(bo.getAnnouncementId());
            push.setTemplateId(null);
            push.setScopeType(null);
            push.setScopeValues(null);
            push.setVarsJson(null);
        } else {
            if (bo.getTemplateId() == null || bo.getScopeType() == null) {
                throw new ServiceException("NT_PUSH_INVALID 模板推送必须指定模板与受众", 400);
            }
            OaNoticeTemplate template = templateMapper.selectById(bo.getTemplateId());
            if (template == null) {
                throw new ServiceException("NT_PUSH_INVALID 关联模板不存在", 400);
            }
            int scopeType = Integer.parseInt(bo.getScopeType());
            audienceResolver.resolve(scopeType, bo.getScopeValues());
            Set<String> declared = NoticeTemplateRenderer.checkDeclared(
                template.getTitleTpl(), template.getContentTpl(),
                NoticeTemplateServiceImpl.parseVars(template.getVarsJson()));
            Map<String, String> values = bo.getVars() == null ? Map.of() : bo.getVars();
            NoticeTemplateRenderer.render(template.getTitleTpl(), declared, values);
            NoticeTemplateRenderer.render(template.getContentTpl(), declared, values);
            push.setAnnouncementId(null);
            push.setTemplateId(bo.getTemplateId());
            push.setScopeType(scopeType);
            push.setScopeValues(bo.getScopeValues());
            push.setVarsJson(values.isEmpty() ? null : JsonUtils.toJsonString(values));
        }
        if (scheduleType == ScheduleType.ONCE) {
            if (bo.getRunAt() == null) {
                throw new ServiceException("NT_PUSH_INVALID 单次推送必须指定执行时间", 400);
            }
            if (!bo.getRunAt().isAfter(LocalDateTime.now())) {
                throw new ServiceException("NT_PUSH_INVALID 执行时间必须晚于当前时间", 400);
            }
            push.setCronExpr(null);
            push.setRunAt(bo.getRunAt());
            push.setNextRunTime(bo.getRunAt());
        } else {
            if (bo.getCronExpr() == null || bo.getCronExpr().isBlank()) {
                throw new ServiceException("NT_PUSH_INVALID 周期推送必须指定周期表达式", 400);
            }
            LocalDateTime next = PushCron.parse(bo.getCronExpr()).nextAfter(LocalDateTime.now());
            if (next == null) {
                throw new ServiceException("NT_PUSH_INVALID 周期表达式在未来一年内无执行时间", 400);
            }
            push.setRunAt(null);
            push.setCronExpr(bo.getCronExpr().trim());
            push.setNextRunTime(next);
        }
    }

    static String eventKey(Long pushId, LocalDateTime slot) {
        return "SCHED-" + pushId + "-" + slot.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    static Map<String, String> parseVarValues(String varsJson) {
        if (varsJson == null || varsJson.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = JsonUtils.parseObject(varsJson,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
            });
        return values == null ? Map.of() : new LinkedHashMap<>(values);
    }

    private boolean pushEnabled(Long userId, String msgType) {
        Integer enabled = preferenceMapper.selectEnabled(userId, msgType);
        return enabled == null || enabled != 0;
    }

    private String truncate(RuntimeException error) {
        String message = error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : ": " + error.getMessage());
        return message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message;
    }

    private OaScheduledPush require(Long id) {
        OaScheduledPush push = pushMapper.selectById(id);
        if (push == null) {
            throw new ServiceException("NT_PUSH_NOT_FOUND 推送任务不存在", 404);
        }
        return push;
    }

    private ScheduledPushVo toVo(OaScheduledPush push) {
        ScheduledPushVo vo = new ScheduledPushVo();
        vo.setId(push.getId());
        vo.setName(push.getName());
        vo.setPushType(push.getPushType());
        vo.setAnnouncementId(push.getAnnouncementId());
        vo.setTemplateId(push.getTemplateId());
        vo.setScopeType(push.getScopeType());
        vo.setScopeValues(push.getScopeValues());
        vo.setVars(parseVarValues(push.getVarsJson()));
        vo.setScheduleType(push.getScheduleType());
        vo.setRunAt(push.getRunAt());
        vo.setCronExpr(push.getCronExpr());
        vo.setNextRunTime(push.getNextRunTime());
        vo.setLastRunTime(push.getLastRunTime());
        vo.setRunCount(push.getRunCount());
        vo.setLastError(push.getLastError());
        vo.setStatus(push.getStatus());
        vo.setRemark(push.getRemark());
        vo.setCreateTime(push.getCreateTime());
        if (push.getAnnouncementId() != null) {
            OaAnnouncement announcement = announcementMapper.selectById(push.getAnnouncementId());
            vo.setAnnouncementTitle(announcement == null ? null : announcement.getTitle());
        }
        if (push.getTemplateId() != null) {
            OaNoticeTemplate template = templateMapper.selectById(push.getTemplateId());
            if (template != null) {
                vo.setTemplateCode(template.getTemplateCode());
                vo.setTemplateName(template.getName());
            }
        }
        return vo;
    }

    private PushRunVo toRunVo(OaScheduledPushRun run) {
        PushRunVo vo = new PushRunVo();
        vo.setId(run.getId());
        vo.setPushId(run.getPushId());
        vo.setEventKey(run.getEventKey());
        vo.setSlotTime(run.getSlotTime());
        vo.setStatus(run.getStatus());
        vo.setReceiverCount(run.getReceiverCount());
        vo.setError(run.getError());
        vo.setCreateTime(run.getCreateTime());
        return vo;
    }
}
