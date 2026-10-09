package org.dromara.agentoa.collaboration;

import org.dromara.agentoa.collaboration.domain.bo.CollaborationCommentBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskStatusBo;
import org.dromara.agentoa.collaboration.domain.enums.TaskStatus;
import org.dromara.agentoa.collaboration.support.CollaborationTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务状态机、终态保护与协作通知（docs/17 第 5 步）：非法跳转、已完成任务修改、通知去重。
 */
class TaskH2Test {

    @BeforeAll
    static void boot() {
        CollaborationTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        CollaborationTestEnvironment.clearData();
        CollaborationTestEnvironment.logout();
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, "指派人");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ATTENDEE,
            CollaborationTestEnvironment.DEPT_A, "负责人");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, "陌生人");
        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, Set.of("cl:task:add", "cl:task:edit", "cl:task:remove"));
    }

    @Test
    void statusMachineRejectsIllegalJumpAndLocksDoneTask() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("状态机任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        var inProgress = CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(task.getLockVersion(), TaskStatus.IN_PROGRESS, null), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(inProgress.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS.code());

        var done = CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(inProgress.getLockVersion(), TaskStatus.DONE, "完成"), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(done.getStatus()).isEqualTo(TaskStatus.DONE.code());
        assertThat(done.getProgress()).isEqualTo(100);
        assertThat(done.getCompletedTime()).isNotNull();

        // 非法跳转与终态修改
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(done.getLockVersion(), TaskStatus.TODO, null), CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_TASK_STATE_CONFLICT");
        var edit = bo("改标题", CollaborationTestEnvironment.USER_ATTENDEE);
        edit.setLockVersion(done.getLockVersion());
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.update(task.getId(), edit,
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_TASK_STATE_CONFLICT");
    }

    @Test
    void illegalTransitionRejected() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("非法跳转", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(task.getLockVersion(), TaskStatus.BLOCKED, null), CollaborationTestEnvironment.USER_ORGANIZER);
        // BLOCKED -> DONE 非法
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(task.getLockVersion() + 1, TaskStatus.DONE, null), CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_TASK_STATE_INVALID");
    }

    @Test
    void staleLockVersionConflicts() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("锁版本任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.changeStatus(task.getId(),
            status(99, TaskStatus.IN_PROGRESS, null), CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("VERSION_CONFLICT");
    }

    @Test
    void strangerCannotSeeOrEditTask() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("越权任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, Set.of("cl:task:add", "cl:task:edit", "cl:task:remove"));
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.detail(task.getId(),
            CollaborationTestEnvironment.USER_STRANGER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_TASK_NOT_FOUND");
        assertThatThrownBy(() -> CollaborationTestEnvironment.taskService.update(task.getId(),
            bo("越权修改", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_STRANGER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_FORBIDDEN");
    }

    @Test
    void commentNotifiesAssigneeAndMentionsWithoutDuplicates() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("评论任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        // 创建时给负责人一条指派通知
        assertThat(CollaborationTestEnvironment.outboxCount()).isEqualTo(1);

        CollaborationCommentBo comment = new CollaborationCommentBo();
        comment.setContent("请看一下");
        comment.setMentionIds(List.of(String.valueOf(CollaborationTestEnvironment.USER_ORGANIZER),
            String.valueOf(CollaborationTestEnvironment.USER_ATTENDEE)));
        CollaborationTestEnvironment.taskService.comment(task.getId(), comment, CollaborationTestEnvironment.USER_ORGANIZER);
        // 评论通知去重：指派人是操作者跳过，负责人虽被提及只收一条
        assertThat(CollaborationTestEnvironment.outboxCount()).isEqualTo(2);

        var activities = CollaborationTestEnvironment.taskService.activities(task.getId(),
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(activities).extracting("activityType").contains(1, 4);
    }

    @Test
    void memberAddIsIdempotentAndNotifiesOnce() {
        var task = CollaborationTestEnvironment.taskService.create(
            bo("协作任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        long base = CollaborationTestEnvironment.outboxCount();
        CollaborationTestEnvironment.taskService.addMember(task.getId(),
            String.valueOf(CollaborationTestEnvironment.USER_STRANGER), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.taskService.addMember(task.getId(),
            String.valueOf(CollaborationTestEnvironment.USER_STRANGER), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(CollaborationTestEnvironment.outboxCount()).isEqualTo(base + 1);
        assertThat(CollaborationTestEnvironment.members.selectUserIds(task.getId()))
            .containsExactly(CollaborationTestEnvironment.USER_STRANGER);

        // 协作者可见任务
        var detail = CollaborationTestEnvironment.taskService.detail(task.getId(), CollaborationTestEnvironment.USER_STRANGER);
        assertThat(detail.getMembers()).hasSize(1);
    }

    @Test
    void boardGroupsTasksByStatus() {
        CollaborationTestEnvironment.taskService.create(
            bo("看板任务", CollaborationTestEnvironment.USER_ATTENDEE), CollaborationTestEnvironment.USER_ORGANIZER);
        var board = CollaborationTestEnvironment.taskService.board(CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(board.getColumns()).hasSize(5);
        assertThat(board.getColumns().get(0).getStatus()).isEqualTo(TaskStatus.TODO.code());
        assertThat(board.getColumns().get(0).getTasks()).hasSize(1);
    }

    private static TaskBo bo(String title, Long assigneeId) {
        TaskBo bo = new TaskBo();
        bo.setTitle(title);
        bo.setDescription("任务描述");
        bo.setAssigneeId(String.valueOf(assigneeId));
        bo.setPriority("2");
        bo.setDueDate("2026-10-10");
        return bo;
    }

    private static TaskStatusBo status(Integer lockVersion, TaskStatus target, String comment) {
        TaskStatusBo bo = new TaskStatusBo();
        bo.setLockVersion(lockVersion);
        bo.setStatus(String.valueOf(target.code()));
        bo.setComment(comment);
        return bo;
    }
}
