package org.dromara.agentoa.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.OaTask;
import org.dromara.agentoa.collaboration.domain.OaTaskActivity;
import org.dromara.agentoa.collaboration.domain.bo.CollaborationCommentBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskPageQuery;
import org.dromara.agentoa.collaboration.domain.bo.TaskProgressBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskStatusBo;
import org.dromara.agentoa.collaboration.domain.enums.ActivityType;
import org.dromara.agentoa.collaboration.domain.enums.TaskPriority;
import org.dromara.agentoa.collaboration.domain.enums.TaskStatus;
import org.dromara.agentoa.collaboration.domain.policy.CollaborationAccessPolicy;
import org.dromara.agentoa.collaboration.domain.vo.BoardVo;
import org.dromara.agentoa.collaboration.domain.vo.TaskActivityVo;
import org.dromara.agentoa.collaboration.domain.vo.CollabTaskVo;
import org.dromara.agentoa.collaboration.mapper.CollaborationIdentityReadMapper;
import org.dromara.agentoa.collaboration.mapper.OaTaskActivityMapper;
import org.dromara.agentoa.collaboration.mapper.OaTaskMapper;
import org.dromara.agentoa.collaboration.mapper.TaskMemberMapper;
import org.dromara.agentoa.collaboration.service.ITaskService;
import org.dromara.agentoa.collaboration.service.support.CollaborationOutboxWriter;
import org.dromara.agentoa.collaboration.service.support.TimeUtil;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 任务实现（docs/17 第 5 步）：状态机 TODO/IN_PROGRESS/BLOCKED/DONE/CANCELLED，
 * 终态不可修改；活动与评论写 oa_task_activity；通知经 outbox 幂等。
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements ITaskService {

    private final OaTaskMapper taskMapper;
    private final OaTaskActivityMapper activityMapper;
    private final TaskMemberMapper memberMapper;
    private final CollaborationIdentityReadMapper identityMapper;
    private final CollaborationOutboxWriter outboxWriter;

    @Override
    public PageVo<CollabTaskVo> list(TaskPageQuery query, Long actorUserId) {
        LambdaQueryWrapper<OaTask> wrapper = new LambdaQueryWrapper<OaTask>().eq(OaTask::getDelFlag, 0);
        if ("created".equalsIgnoreCase(query.getScope())) {
            wrapper.eq(OaTask::getAssignerId, actorUserId);
        } else {
            List<Long> memberTaskIds = memberMapper.selectTaskIds(actorUserId);
            wrapper.and(w -> {
                w.eq(OaTask::getAssigneeId, actorUserId).or().eq(OaTask::getAssignerId, actorUserId);
                if (!memberTaskIds.isEmpty()) {
                    w.or().in(OaTask::getId, memberTaskIds);
                }
            });
        }
        if (query.getStatus() != null) {
            wrapper.eq(OaTask::getStatus, query.getStatus());
        }
        if (query.getAssigneeId() != null && !query.getAssigneeId().isBlank()) {
            wrapper.eq(OaTask::getAssigneeId, Long.valueOf(query.getAssigneeId().trim()));
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaTask::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByDesc(OaTask::getCreateTime);
        IPage<OaTask> result = taskMapper.selectPage(new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<CollabTaskVo> records = new ArrayList<>();
        for (OaTask task : result.getRecords()) {
            records.add(toVo(task, actorUserId));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public CollabTaskVo detail(Long id, Long actorUserId) {
        OaTask task = require(id);
        requireViewable(task, actorUserId);
        return toVo(task, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabTaskVo create(TaskBo bo, Long actorUserId) {
        Long assigneeId = bo.getAssigneeId() == null || bo.getAssigneeId().isBlank()
            ? actorUserId : Long.valueOf(bo.getAssigneeId().trim());
        requireUser(assigneeId);
        Date now = new Date();
        OaTask task = new OaTask();
        task.setLockVersion(0);
        task.setTitle(bo.getTitle().trim());
        task.setDescription(bo.getDescription());
        task.setParentId(bo.getParentId() == null || bo.getParentId().isBlank() ? null : Long.valueOf(bo.getParentId().trim()));
        task.setAssignerId(actorUserId);
        task.setAssigneeId(assigneeId);
        task.setPriority(TaskPriority.from(bo.getPriority() == null ? null : Integer.valueOf(bo.getPriority())).code());
        task.setStatus(TaskStatus.TODO.code());
        task.setStartDate(TimeUtil.parseDate(bo.getStartDate(), "startDate"));
        task.setDueDate(TimeUtil.parseDate(bo.getDueDate(), "dueDate"));
        task.setProgress(0);
        task.setTags(bo.getTags());
        task.setCreateBy(actorUserId);
        task.setCreateTime(now);
        task.setUpdateBy(actorUserId);
        task.setUpdateTime(now);
        task.setDelFlag(0);
        taskMapper.insert(task);
        syncMembers(task.getId(), bo.getMemberIds(), actorUserId, now, false);
        writeActivity(task.getId(), ActivityType.CREATE, "创建任务", actorUserId, null, now);
        if (!assigneeId.equals(actorUserId)) {
            outboxWriter.writeTodo("CL-ASSIGN-" + task.getId() + "-" + assigneeId + "-V0", assigneeId,
                "新任务：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 给你指派了任务「" + task.getTitle() + "」",
                "task", task.getId());
        }
        return toVo(task, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabTaskVo update(Long id, TaskBo bo, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        requireEditable(task);
        Long oldAssignee = task.getAssigneeId();
        Date now = new Date();
        task.setTitle(bo.getTitle().trim());
        task.setDescription(bo.getDescription());
        task.setParentId(bo.getParentId() == null || bo.getParentId().isBlank() ? null : Long.valueOf(bo.getParentId().trim()));
        Long assigneeId = bo.getAssigneeId() == null || bo.getAssigneeId().isBlank()
            ? task.getAssigneeId() : Long.valueOf(bo.getAssigneeId().trim());
        requireUser(assigneeId);
        task.setAssigneeId(assigneeId);
        task.setPriority(TaskPriority.from(bo.getPriority() == null ? null : Integer.valueOf(bo.getPriority())).code());
        task.setStartDate(TimeUtil.parseDate(bo.getStartDate(), "startDate"));
        task.setDueDate(TimeUtil.parseDate(bo.getDueDate(), "dueDate"));
        task.setTags(bo.getTags());
        task.setUpdateBy(actorUserId);
        task.setUpdateTime(now);
        int nextVersion = task.getLockVersion() == null ? 1 : task.getLockVersion() + 1;
        task.setLockVersion(null);
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<OaTask>()
            .eq(OaTask::getId, id)
            .eq(OaTask::getLockVersion, requireVersion(bo.getLockVersion()))
            .set(OaTask::getLockVersion, nextVersion));
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 任务已被他人修改", 409);
        }
        task.setLockVersion(nextVersion);
        syncMembers(id, bo.getMemberIds(), actorUserId, now, true);
        if (!assigneeId.equals(oldAssignee)) {
            writeActivity(id, ActivityType.MEMBER,
                "负责人变更为 " + identityMapper.selectNickName(assigneeId), actorUserId, null, now);
            outboxWriter.writeTodo("CL-ASSIGN-" + id + "-" + assigneeId + "-V" + nextVersion, assigneeId,
                "任务指派：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 把任务「" + task.getTitle() + "」指派给了你",
                "task", id);
        }
        return toVo(task, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        requireEditable(task);
        taskMapper.update(null, new LambdaUpdateWrapper<OaTask>()
            .eq(OaTask::getId, id)
            .set(OaTask::getDelFlag, 1)
            .set(OaTask::getUpdateBy, actorUserId)
            .set(OaTask::getUpdateTime, new Date()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabTaskVo changeStatus(Long id, TaskStatusBo bo, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        TaskStatus current = TaskStatus.from(task.getStatus());
        TaskStatus target = TaskStatus.from(Integer.valueOf(bo.getStatus()));
        if (current.terminal()) {
            throw new ServiceException("CL_TASK_STATE_CONFLICT 已完成或已取消任务不可再变更", 409);
        }
        if (!current.canTransitionTo(target)) {
            throw new ServiceException("CL_TASK_STATE_INVALID 状态跳转非法：" + current + " -> " + target, 409);
        }
        Date now = new Date();
        int nextVersion = task.getLockVersion() == null ? 1 : task.getLockVersion() + 1;
        LambdaUpdateWrapper<OaTask> wrapper = new LambdaUpdateWrapper<OaTask>()
            .eq(OaTask::getId, id)
            .eq(OaTask::getStatus, current.code())
            .eq(OaTask::getLockVersion, requireVersion(bo.getLockVersion()))
            .set(OaTask::getStatus, target.code())
            .set(OaTask::getLockVersion, nextVersion)
            .set(OaTask::getUpdateBy, actorUserId)
            .set(OaTask::getUpdateTime, now);
        if (target == TaskStatus.DONE) {
            wrapper.set(OaTask::getCompletedTime, now).set(OaTask::getProgress, 100);
        }
        int updated = taskMapper.update(null, wrapper);
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 任务已被他人修改", 409);
        }
        task.setStatus(target.code());
        task.setLockVersion(nextVersion);
        if (target == TaskStatus.DONE) {
            task.setCompletedTime(now);
            task.setProgress(100);
        }
        String content = "状态变更 " + current + " -> " + target
            + (bo.getComment() == null || bo.getComment().isBlank() ? "" : "：" + bo.getComment().trim());
        writeActivity(id, ActivityType.STATUS, content, actorUserId, null, now);
        Long counterpart = actorUserId.equals(task.getAssigneeId()) ? task.getAssignerId() : task.getAssigneeId();
        if (counterpart != null && !counterpart.equals(actorUserId)) {
            outboxWriter.writeTodo("CL-STATUS-" + id + "-V" + nextVersion + "-" + counterpart, counterpart,
                "任务更新：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 将任务「" + task.getTitle() + "」更新为 " + target,
                "task", id);
        }
        return toVo(task, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabTaskVo changeProgress(Long id, TaskProgressBo bo, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        requireEditable(task);
        Date now = new Date();
        int nextVersion = task.getLockVersion() == null ? 1 : task.getLockVersion() + 1;
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<OaTask>()
            .eq(OaTask::getId, id)
            .eq(OaTask::getLockVersion, requireVersion(bo.getLockVersion()))
            .set(OaTask::getProgress, bo.getProgress())
            .set(OaTask::getLockVersion, nextVersion)
            .set(OaTask::getUpdateBy, actorUserId)
            .set(OaTask::getUpdateTime, now));
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 任务已被他人修改", 409);
        }
        task.setProgress(bo.getProgress());
        task.setLockVersion(nextVersion);
        writeActivity(id, ActivityType.PROGRESS, "进度更新为 " + bo.getProgress() + "%"
            + (bo.getComment() == null || bo.getComment().isBlank() ? "" : "：" + bo.getComment().trim()),
            actorUserId, null, now);
        return toVo(task, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskActivityVo comment(Long id, CollaborationCommentBo bo, Long actorUserId) {
        OaTask task = require(id);
        if (!CollaborationAccessPolicy.canViewTask(actorUserId, task.getAssignerId(), task.getAssigneeId(),
            memberIds(id))) {
            throw new ServiceException("CL_FORBIDDEN 无权参与该任务", 403);
        }
        Date now = new Date();
        String mentions = bo.getMentionIds() == null ? null : String.join(",", bo.getMentionIds());
        OaTaskActivity activity = writeActivity(id, ActivityType.COMMENT, bo.getContent().trim(), actorUserId, mentions, now);
        Set<Long> receivers = new LinkedHashSet<>();
        receivers.add(task.getAssigneeId());
        receivers.add(task.getAssignerId());
        receivers.addAll(memberIds(id));
        receivers.addAll(parseIds(bo.getMentionIds()));
        receivers.remove(actorUserId);
        for (Long receiver : receivers) {
            if (receiver == null) {
                continue;
            }
            outboxWriter.writeNotice("CL-CMT-" + activity.getId() + "-" + receiver, receiver,
                "任务评论：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 评论了任务「" + task.getTitle() + "」",
                "task", id);
        }
        return toActivityVo(activity);
    }

    @Override
    public List<TaskActivityVo> activities(Long id, Long actorUserId) {
        OaTask task = require(id);
        if (!CollaborationAccessPolicy.canViewTask(actorUserId, task.getAssignerId(), task.getAssigneeId(),
            memberIds(id))) {
            throw new ServiceException("CL_FORBIDDEN 无权查看该任务", 403);
        }
        List<OaTaskActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<OaTaskActivity>()
            .eq(OaTaskActivity::getTaskId, id)
            .eq(OaTaskActivity::getDelFlag, 0)
            .orderByAsc(OaTaskActivity::getCreateTime)
            .orderByAsc(OaTaskActivity::getId));
        List<TaskActivityVo> records = new ArrayList<>();
        for (OaTaskActivity activity : activities) {
            records.add(toActivityVo(activity));
        }
        return records;
    }

    @Override
    public BoardVo board(Long actorUserId) {
        List<OaTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<OaTask>()
            .eq(OaTask::getDelFlag, 0)
            .and(w -> {
                w.eq(OaTask::getAssigneeId, actorUserId).or().eq(OaTask::getAssignerId, actorUserId);
                List<Long> memberTaskIds = memberMapper.selectTaskIds(actorUserId);
                if (!memberTaskIds.isEmpty()) {
                    w.or().in(OaTask::getId, memberTaskIds);
                }
            })
            .orderByAsc(OaTask::getDueDate));
        BoardVo board = new BoardVo();
        for (TaskStatus status : TaskStatus.values()) {
            BoardVo.Column column = new BoardVo.Column();
            column.setStatus(status.code());
            column.setStatusName(status.name());
            for (OaTask task : tasks) {
                if (task.getStatus() != null && task.getStatus() == status.code()) {
                    column.getTasks().add(toVo(task, actorUserId));
                }
            }
            board.getColumns().add(column);
        }
        return board;
    }

    @Override
    public List<CollabTaskVo.Member> members(Long id, Long actorUserId) {
        OaTask task = require(id);
        requireViewable(task, actorUserId);
        List<CollabTaskVo.Member> members = new ArrayList<>();
        for (Long userId : memberIds(id)) {
            members.add(toMember(userId));
        }
        return members;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabTaskVo.Member addMember(Long id, String userId, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        requireEditable(task);
        Long memberId = Long.valueOf(userId.trim());
        requireUser(memberId);
        Date now = new Date();
        if (memberMapper.exists(id, memberId) == 0) {
            memberMapper.insert(id, memberId, actorUserId, now);
            writeActivity(id, ActivityType.MEMBER,
                "添加协作者 " + identityMapper.selectNickName(memberId), actorUserId, null, now);
            outboxWriter.writeNotice("CL-MEM-" + id + "-" + memberId, memberId,
                "任务协作：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 邀请你协作任务「" + task.getTitle() + "」",
                "task", id);
        }
        return toMember(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long id, String userId, Long actorUserId) {
        OaTask task = require(id);
        requireManageable(task, actorUserId);
        requireEditable(task);
        Long memberId = Long.valueOf(userId.trim());
        Date now = new Date();
        if (memberMapper.delete(id, memberId) > 0) {
            writeActivity(id, ActivityType.MEMBER,
                "移除协作者 " + identityMapper.selectNickName(memberId), actorUserId, null, now);
            outboxWriter.writeNotice("CL-MEMR-" + id + "-" + memberId, memberId,
                "任务协作变更：" + task.getTitle(),
                identityMapper.selectNickName(actorUserId) + " 将你移出任务「" + task.getTitle() + "」",
                "task", id);
        }
    }

    // ------------------------------------------------------------ internal

    private OaTask require(Long id) {
        OaTask task = taskMapper.selectById(id);
        if (task == null || task.getDelFlag() != null && task.getDelFlag() != 0) {
            throw new ServiceException("CL_TASK_NOT_FOUND 任务不存在", 404);
        }
        return task;
    }

    private void requireViewable(OaTask task, Long actorUserId) {
        if (!CollaborationAccessPolicy.canViewTask(actorUserId, task.getAssignerId(), task.getAssigneeId(),
            memberIds(task.getId()))) {
            throw new ServiceException("CL_TASK_NOT_FOUND 任务不存在", 404);
        }
    }

    private void requireManageable(OaTask task, Long actorUserId) {
        if (!CollaborationAccessPolicy.canManageTask(actorUserId, task.getAssignerId(), task.getAssigneeId())) {
            throw new ServiceException("CL_FORBIDDEN 仅指派人或负责人可操作该任务", 403);
        }
    }

    /** 终态任务拒绝任何修改（docs/17 已完成任务修改测试） */
    private void requireEditable(OaTask task) {
        if (TaskStatus.from(task.getStatus()).terminal()) {
            throw new ServiceException("CL_TASK_STATE_CONFLICT 已完成或已取消任务不可修改", 409);
        }
    }

    private void requireUser(Long userId) {
        List<Long> existing = identityMapper.selectExistingUserIds(List.of(userId));
        if (existing.isEmpty()) {
            throw new ServiceException("CL_ATTENDEE_INVALID 账号不存在", 400);
        }
    }

    private Set<Long> memberIds(Long taskId) {
        return new LinkedHashSet<>(memberMapper.selectUserIds(taskId));
    }

    private void syncMembers(Long taskId, List<String> memberIds, Long actorUserId, Date now, boolean replace) {
        Set<Long> target = parseIds(memberIds);
        if (replace) {
            for (Long existing : memberIds(taskId)) {
                if (!target.contains(existing)) {
                    memberMapper.delete(taskId, existing);
                    writeActivity(taskId, ActivityType.MEMBER,
                        "移除协作者 " + identityMapper.selectNickName(existing), actorUserId, null, now);
                }
            }
        }
        for (Long userId : target) {
            if (memberMapper.exists(taskId, userId) == 0) {
                requireUser(userId);
                memberMapper.insert(taskId, userId, actorUserId, now);
                writeActivity(taskId, ActivityType.MEMBER,
                    "添加协作者 " + identityMapper.selectNickName(userId), actorUserId, null, now);
                outboxWriter.writeNotice("CL-MEM-" + taskId + "-" + userId, userId,
                    "任务协作", identityMapper.selectNickName(actorUserId) + " 邀请你协作任务", "task", taskId);
            }
        }
    }

    private OaTaskActivity writeActivity(Long taskId, ActivityType type, String content,
                                         Long operatorId, String mentionIds, Date now) {
        OaTaskActivity activity = new OaTaskActivity();
        activity.setTaskId(taskId);
        activity.setActivityType(type.code());
        activity.setContent(content);
        activity.setOperatorId(operatorId);
        activity.setMentionIds(mentionIds);
        activity.setCreateTime(now);
        activity.setDelFlag(0);
        activityMapper.insert(activity);
        return activity;
    }

    private Set<Long> parseIds(List<String> values) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values != null) {
            for (String raw : values) {
                if (raw != null && !raw.isBlank()) {
                    ids.add(Long.valueOf(raw.trim()));
                }
            }
        }
        return ids;
    }

    private Integer requireVersion(Integer lockVersion) {
        return lockVersion == null ? -1 : lockVersion;
    }

    private CollabTaskVo.Member toMember(Long userId) {
        CollabTaskVo.Member member = new CollabTaskVo.Member();
        member.setUserId(userId);
        member.setNickname(identityMapper.selectNickName(userId));
        return member;
    }

    private TaskActivityVo toActivityVo(OaTaskActivity activity) {
        TaskActivityVo vo = new TaskActivityVo();
        vo.setId(activity.getId());
        vo.setTaskId(activity.getTaskId());
        vo.setActivityType(activity.getActivityType());
        vo.setContent(activity.getContent());
        vo.setOperatorId(activity.getOperatorId());
        vo.setOperatorName(identityMapper.selectNickName(activity.getOperatorId()));
        vo.setMentionIds(activity.getMentionIds());
        vo.setCreateTime(TimeUtil.format(activity.getCreateTime()));
        return vo;
    }

    private CollabTaskVo toVo(OaTask task, Long actorUserId) {
        CollabTaskVo vo = new CollabTaskVo();
        vo.setId(task.getId());
        vo.setLockVersion(task.getLockVersion());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setParentId(task.getParentId());
        vo.setAssignerId(task.getAssignerId());
        vo.setAssignerName(identityMapper.selectNickName(task.getAssignerId()));
        vo.setAssigneeId(task.getAssigneeId());
        vo.setAssigneeName(identityMapper.selectNickName(task.getAssigneeId()));
        vo.setPriority(task.getPriority());
        vo.setStatus(task.getStatus());
        vo.setStartDate(TimeUtil.formatDate(task.getStartDate()));
        vo.setDueDate(TimeUtil.formatDate(task.getDueDate()));
        vo.setCompletedTime(TimeUtil.format(task.getCompletedTime()));
        vo.setProgress(task.getProgress());
        vo.setTags(task.getTags());
        List<CollabTaskVo.Member> members = new ArrayList<>();
        for (Long userId : memberIds(task.getId())) {
            members.add(toMember(userId));
        }
        vo.setMembers(members);
        vo.setCanManage(CollaborationAccessPolicy.canManageTask(actorUserId, task.getAssignerId(), task.getAssigneeId()));
        vo.setCreateTime(TimeUtil.format(task.getCreateTime()));
        vo.setUpdateTime(TimeUtil.format(task.getUpdateTime()));
        return vo;
    }
}
