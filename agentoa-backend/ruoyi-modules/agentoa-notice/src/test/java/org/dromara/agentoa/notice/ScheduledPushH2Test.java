package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.domain.bo.AnnouncementBo;
import org.dromara.agentoa.notice.domain.bo.ScheduledPushBo;
import org.dromara.agentoa.notice.support.NoticeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 定时推送（NC-04）：DB 租约调度 + event_key 幂等、暂停、定时公告受众一致、失败记录不重试。
 */
class ScheduledPushH2Test {

    @BeforeAll
    static void boot() {
        NoticeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        NoticeTestEnvironment.clearData();
        NoticeTestEnvironment.logout();
        NoticeTestEnvironment.seedDept(NoticeTestEnvironment.DEPT_A, "部门A");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A, "人力");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_EMPLOYEE, NoticeTestEnvironment.DEPT_A, "员工");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_OTHER, NoticeTestEnvironment.DEPT_A, "他人");
        NoticeTestEnvironment.loginAs(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A,
            java.util.Set.of("nt:schedule:add", "nt:schedule:edit", "nt:schedule:remove", "nt:schedule:run",
                "nt:notice:add"));
    }

    @Test
    void templatePushRunsOnceAndRescanDoesNotDuplicate() {
        var template = NoticeTestEnvironment.seedTemplate("tpl_sched", "定时模板", "Hi {name}", "{name} 请打卡",
            "[\"name\"]", "NOTICE", 1);
        ScheduledPushBo bo = new ScheduledPushBo();
        bo.setName("模板推送");
        bo.setPushType("2");
        bo.setTemplateId(template.getId());
        bo.setScopeType("4");
        bo.setScopeValues(String.valueOf(NoticeTestEnvironment.USER_EMPLOYEE));
        bo.setVars(Map.of("name", "张三"));
        bo.setScheduleType("1");
        bo.setRunAt(LocalDateTime.now().plusHours(1));
        var push = NoticeTestEnvironment.scheduledPushService.create(bo, NoticeTestEnvironment.USER_HR);
        forceDue(push.getId(), LocalDateTime.now().minusMinutes(5));

        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(1);

        var runs = NoticeTestEnvironment.pushRuns.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.agentoa.notice.domain.OaScheduledPushRun>()
                .eq(org.dromara.agentoa.notice.domain.OaScheduledPushRun::getPushId, push.getId()));
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo(1);
        assertThat(runs.get(0).getReceiverCount()).isEqualTo(1);
        assertThat(runs.get(0).getEventKey()).startsWith("SCHED-" + push.getId() + "-");
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(1);
        String payload = NoticeTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT payload FROM sys_outbox", String.class);
        assertThat(payload).contains("Hi 张三").contains("张三 请打卡");

        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(0);
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(1);
        var after = NoticeTestEnvironment.scheduledPushService.detail(push.getId());
        assertThat(after.getRunCount()).isEqualTo(1);
        assertThat(after.getNextRunTime()).isNull();
    }

    @Test
    void pausedPushIsNotExecutedAndResumeRearms() {
        var template = NoticeTestEnvironment.seedTemplate("tpl_pause", "暂停", "t", "c", "[]", "NOTICE", 1);
        ScheduledPushBo bo = new ScheduledPushBo();
        bo.setName("暂停任务");
        bo.setPushType("2");
        bo.setTemplateId(template.getId());
        bo.setScopeType("4");
        bo.setScopeValues(String.valueOf(NoticeTestEnvironment.USER_EMPLOYEE));
        bo.setScheduleType("1");
        bo.setRunAt(LocalDateTime.now().plusHours(1));
        var push = NoticeTestEnvironment.scheduledPushService.create(bo, NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.scheduledPushService.pause(push.getId());
        forceDue(push.getId(), LocalDateTime.now().minusMinutes(5));

        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(0);
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(0);

        NoticeTestEnvironment.scheduledPushService.resume(push.getId());
        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(1);
    }

    @Test
    void scheduledAnnouncementMatchesManualPublishAudience() {
        AnnouncementBo scope = new AnnouncementBo();
        scope.setTitle("定时公告");
        scope.setContent("内容");
        scope.setNoticeType("company");
        scope.setScopeType("4");
        scope.setScopeValues(NoticeTestEnvironment.USER_EMPLOYEE + "," + NoticeTestEnvironment.USER_OTHER);

        var auto = NoticeTestEnvironment.announcementService.create(scope, NoticeTestEnvironment.USER_HR);
        ScheduledPushBo bo = new ScheduledPushBo();
        bo.setName("定时发布");
        bo.setPushType("1");
        bo.setAnnouncementId(auto.getId());
        bo.setScheduleType("1");
        bo.setRunAt(LocalDateTime.now().plusHours(1));
        var push = NoticeTestEnvironment.scheduledPushService.create(bo, NoticeTestEnvironment.USER_HR);
        forceDue(push.getId(), LocalDateTime.now().minusMinutes(5));

        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(1);

        scope.setTitle("手动公告");
        var manual = NoticeTestEnvironment.announcementService.create(scope, NoticeTestEnvironment.USER_HR);
        var manualPublished = NoticeTestEnvironment.announcementService.publish(manual.getId(), "manual-1",
            NoticeTestEnvironment.USER_HR);

        var autoPublished = NoticeTestEnvironment.announcementService.detail(auto.getId(), NoticeTestEnvironment.USER_HR);
        assertThat(autoPublished.getStatus()).isEqualTo(manualPublished.getStatus());
        assertThat(autoPublished.getAudienceCount()).isEqualTo(manualPublished.getAudienceCount());
        assertThat(NoticeTestEnvironment.audience.selectUserIds(auto.getId()))
            .containsExactlyInAnyOrderElementsOf(NoticeTestEnvironment.audience.selectUserIds(manual.getId()));
    }

    @Test
    void cronPushRollsForwardAndSkipsMissedSlots() {
        var template = NoticeTestEnvironment.seedTemplate("tpl_cron", "周期", "t", "c", "[]", "NOTICE", 1);
        ScheduledPushBo bo = new ScheduledPushBo();
        bo.setName("周期推送");
        bo.setPushType("2");
        bo.setTemplateId(template.getId());
        bo.setScopeType("4");
        bo.setScopeValues(String.valueOf(NoticeTestEnvironment.USER_EMPLOYEE));
        bo.setScheduleType("2");
        bo.setCronExpr("0 10 * * *");
        var push = NoticeTestEnvironment.scheduledPushService.create(bo, NoticeTestEnvironment.USER_HR);
        assertThat(push.getNextRunTime()).isAfter(LocalDateTime.now());

        LocalDateTime missedSlot = LocalDateTime.of(2026, 1, 1, 10, 0);
        forceDue(push.getId(), missedSlot);
        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(1);

        var run = NoticeTestEnvironment.pushRuns.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.agentoa.notice.domain.OaScheduledPushRun>()
                .eq(org.dromara.agentoa.notice.domain.OaScheduledPushRun::getPushId, push.getId()));
        assertThat(run.getSlotTime()).isEqualTo(missedSlot);
        assertThat(NoticeTestEnvironment.scheduledPushService.detail(push.getId()).getNextRunTime())
            .isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(0);
    }

    @Test
    void failedExecutionIsRecordedOnceAndNotRetried() {
        AnnouncementBo scope = new AnnouncementBo();
        scope.setTitle("空受众公告");
        scope.setContent("内容");
        scope.setNoticeType("company");
        scope.setScopeType("4");
        scope.setScopeValues("999");
        var announcement = NoticeTestEnvironment.announcementService.create(scope, NoticeTestEnvironment.USER_HR);
        ScheduledPushBo bo = new ScheduledPushBo();
        bo.setName("失败任务");
        bo.setPushType("1");
        bo.setAnnouncementId(announcement.getId());
        bo.setScheduleType("1");
        bo.setRunAt(LocalDateTime.now().plusHours(1));
        var push = NoticeTestEnvironment.scheduledPushService.create(bo, NoticeTestEnvironment.USER_HR);
        forceDue(push.getId(), LocalDateTime.now().minusMinutes(5));

        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(0);

        var run = NoticeTestEnvironment.pushRuns.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.dromara.agentoa.notice.domain.OaScheduledPushRun>()
                .eq(org.dromara.agentoa.notice.domain.OaScheduledPushRun::getPushId, push.getId()));
        assertThat(run.getStatus()).isEqualTo(2);
        assertThat(run.getError()).contains("NT_AUDIENCE_EMPTY");
        assertThat(NoticeTestEnvironment.scheduledPushService.executeDue()).isEqualTo(0);
        assertThat(NoticeTestEnvironment.pushRuns.selectCount(null)).isEqualTo(1L);
        assertThat(NoticeTestEnvironment.scheduledPushService.detail(push.getId()).getNextRunTime()).isNull();
    }

    @Test
    void pushCreationValidatesScheduleAndTargets() {
        var template = NoticeTestEnvironment.seedTemplate("tpl_valid", "校验", "t", "c", "[]", "NOTICE", 1);
        ScheduledPushBo bad = new ScheduledPushBo();
        bad.setName("非法");
        bad.setPushType("2");
        bad.setTemplateId(template.getId());
        bad.setScopeType("1");
        bad.setScheduleType("1");
        bad.setRunAt(LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> NoticeTestEnvironment.scheduledPushService.create(bad, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_PUSH_INVALID");

        ScheduledPushBo badCron = new ScheduledPushBo();
        badCron.setName("非法cron");
        badCron.setPushType("2");
        badCron.setTemplateId(template.getId());
        badCron.setScopeType("1");
        badCron.setScheduleType("2");
        badCron.setCronExpr("0 0 * JAN *");
        assertThatThrownBy(() -> NoticeTestEnvironment.scheduledPushService.create(badCron, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_CRON_INVALID");

        ScheduledPushBo missingTemplate = new ScheduledPushBo();
        missingTemplate.setName("无模板");
        missingTemplate.setPushType("2");
        missingTemplate.setTemplateId(999L);
        missingTemplate.setScopeType("1");
        missingTemplate.setScheduleType("1");
        missingTemplate.setRunAt(LocalDateTime.now().plusHours(1));
        assertThatThrownBy(() -> NoticeTestEnvironment.scheduledPushService.create(missingTemplate, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_PUSH_INVALID");
    }

    private void forceDue(Long pushId, LocalDateTime slot) {
        NoticeTestEnvironment.jdbcTemplate().update(
            "UPDATE oa_scheduled_push SET next_run_time = ? WHERE id = ?",
            Timestamp.valueOf(slot), pushId);
    }
}
