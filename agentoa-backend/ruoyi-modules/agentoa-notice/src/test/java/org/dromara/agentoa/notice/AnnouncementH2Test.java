package org.dromara.agentoa.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.notice.domain.OaAnnouncementRead;
import org.dromara.agentoa.notice.domain.bo.AnnouncementBo;
import org.dromara.agentoa.notice.domain.bo.PreferenceBo;
import org.dromara.agentoa.notice.domain.enums.AnnouncementStatus;
import org.dromara.agentoa.notice.support.NoticeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 公告生命周期与受众快照（docs/15）：发布冻结、撤回保留审计、消息 fan-out 幂等。
 */
class AnnouncementH2Test {

    @BeforeAll
    static void boot() {
        NoticeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        NoticeTestEnvironment.clearData();
        NoticeTestEnvironment.logout();
        NoticeTestEnvironment.seedDept(NoticeTestEnvironment.DEPT_A, "部门A");
        NoticeTestEnvironment.seedDept(NoticeTestEnvironment.DEPT_B, "部门B");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A, "人力");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_EMPLOYEE, NoticeTestEnvironment.DEPT_A, "员工");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_OTHER, NoticeTestEnvironment.DEPT_B, "他人");
        NoticeTestEnvironment.loginAs(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A,
            java.util.Set.of("nt:notice:add", "nt:notice:edit", "nt:notice:recall", "nt:notice:remove", "nt:notice:read"));
    }

    @Test
    void publishSnapshotsAudienceAndFansOutMessages() {
        var created = NoticeTestEnvironment.announcementService.create(bo(1, "10,20"), NoticeTestEnvironment.USER_HR);
        var published = NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-1",
            NoticeTestEnvironment.USER_HR);

        assertThat(published.getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED.code());
        assertThat(published.getAudienceCount()).isEqualTo(3);
        List<Long> audience = NoticeTestEnvironment.audience.selectUserIds(created.getId());
        assertThat(audience).containsExactlyInAnyOrder(NoticeTestEnvironment.USER_HR,
            NoticeTestEnvironment.USER_EMPLOYEE, NoticeTestEnvironment.USER_OTHER);

        Long outbox = NoticeTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class);
        assertThat(outbox).isEqualTo(3);
    }

    @Test
    void audienceResolvedByRoleAndByUser() {
        NoticeTestEnvironment.seedRole(23L, "finance", NoticeTestEnvironment.USER_OTHER);
        var byRole = NoticeTestEnvironment.announcementService.create(bo(3, "finance"), NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(byRole.getId(), "pub-role", NoticeTestEnvironment.USER_HR);
        assertThat(NoticeTestEnvironment.audience.selectUserIds(byRole.getId()))
            .containsExactly(NoticeTestEnvironment.USER_OTHER);

        var byUser = NoticeTestEnvironment.announcementService.create(bo(4, String.valueOf(NoticeTestEnvironment.USER_EMPLOYEE)),
            NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(byUser.getId(), "pub-user", NoticeTestEnvironment.USER_HR);
        assertThat(NoticeTestEnvironment.audience.selectUserIds(byUser.getId()))
            .containsExactly(NoticeTestEnvironment.USER_EMPLOYEE);
    }

    @Test
    void publishIsIdempotentAndSecondPublishConflicts() {
        var created = NoticeTestEnvironment.announcementService.create(bo(1, "10"), NoticeTestEnvironment.USER_HR);
        var first = NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-2", NoticeTestEnvironment.USER_HR);
        var replay = NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-2", NoticeTestEnvironment.USER_HR);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(3);

        assertThatThrownBy(() -> NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-3",
            NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_STATE_CONFLICT");
    }

    @Test
    void publishedContentFrozenAndRecallKeepsAudit() {
        var created = NoticeTestEnvironment.announcementService.create(bo(1, "10"), NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-4", NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.markRead(created.getId(), NoticeTestEnvironment.USER_EMPLOYEE);

        assertThatThrownBy(() -> NoticeTestEnvironment.announcementService.update(created.getId(),
            bo(1, "10"), NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_STATE_CONFLICT");

        NoticeTestEnvironment.announcementService.recall(created.getId(), NoticeTestEnvironment.USER_HR);
        assertThat(NoticeTestEnvironment.announcements.selectById(created.getId()).getStatus())
            .isEqualTo(AnnouncementStatus.RECALLED.code());
        // 受众与已读记录保留
        assertThat(NoticeTestEnvironment.audience.count(created.getId())).isEqualTo(3);
        long readRows = NoticeTestEnvironment.reads.selectCount(new LambdaQueryWrapper<OaAnnouncementRead>()
            .eq(OaAnnouncementRead::getNoticeId, created.getId()));
        assertThat(readRows).isEqualTo(1);

        // 撤回后员工不可见
        NoticeTestEnvironment.loginAs(NoticeTestEnvironment.USER_EMPLOYEE, NoticeTestEnvironment.DEPT_A, java.util.Set.of());
        assertThatThrownBy(() -> NoticeTestEnvironment.announcementService.detail(created.getId(),
            NoticeTestEnvironment.USER_EMPLOYEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_NOT_FOUND");
    }

    @Test
    void markReadIsIdempotentAndReadStatusSplitsAudience() {
        var created = NoticeTestEnvironment.announcementService.create(bo(1, "10"), NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-5", NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.markRead(created.getId(), NoticeTestEnvironment.USER_EMPLOYEE);
        NoticeTestEnvironment.announcementService.markRead(created.getId(), NoticeTestEnvironment.USER_EMPLOYEE);

        var status = NoticeTestEnvironment.announcementService.readStatus(created.getId(), NoticeTestEnvironment.USER_HR);
        assertThat(status.getAudienceCount()).isEqualTo(3);
        assertThat(status.getReadCount()).isEqualTo(1);
        assertThat(status.getReadUsers()).hasSize(1);
        assertThat(status.getUnreadUsers()).hasSize(2);
        assertThat(NoticeTestEnvironment.announcements.selectById(created.getId()).getReadCount()).isEqualTo(1);
    }

    @Test
    void strangerCannotSeeOthersAnnouncements() {
        var created = NoticeTestEnvironment.announcementService.create(
            bo(4, String.valueOf(NoticeTestEnvironment.USER_EMPLOYEE)), NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-6", NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.loginAs(NoticeTestEnvironment.USER_OTHER, NoticeTestEnvironment.DEPT_B, java.util.Set.of());
        assertThatThrownBy(() -> NoticeTestEnvironment.announcementService.detail(created.getId(),
            NoticeTestEnvironment.USER_OTHER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_NOT_FOUND");
    }

    @Test
    void preferenceSuppressesPushButNotPublication() {
        PreferenceBo off = new PreferenceBo();
        off.setMsgType("NOTICE");
        off.setEnabled("0");
        NoticeTestEnvironment.preferenceService.update(NoticeTestEnvironment.USER_EMPLOYEE, List.of(off));

        var created = NoticeTestEnvironment.announcementService.create(bo(1, "10,20"), NoticeTestEnvironment.USER_HR);
        NoticeTestEnvironment.announcementService.publish(created.getId(), "pub-7", NoticeTestEnvironment.USER_HR);

        // 受众仍包含员工，但其提醒被抑制（少一条 outbox）
        assertThat(NoticeTestEnvironment.audience.selectUserIds(created.getId()))
            .contains(NoticeTestEnvironment.USER_EMPLOYEE);
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(2);
        // 未配置偏好的用户默认提醒
        assertThat(NoticeTestEnvironment.preferenceService.get(NoticeTestEnvironment.USER_OTHER))
            .anySatisfy(vo -> {
                assertThat(vo.getMsgType()).isEqualTo("NOTICE");
                assertThat(vo.getEnabled()).isEqualTo("1");
            });
    }

    private static AnnouncementBo bo(int scopeType, String scopeValues) {
        AnnouncementBo bo = new AnnouncementBo();
        bo.setTitle("测试公告");
        bo.setContent("公告正文");
        bo.setNoticeType("company");
        bo.setScopeType(String.valueOf(scopeType));
        bo.setScopeValues(scopeValues);
        return bo;
    }
}
