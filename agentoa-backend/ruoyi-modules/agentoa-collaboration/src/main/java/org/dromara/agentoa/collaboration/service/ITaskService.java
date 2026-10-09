package org.dromara.agentoa.collaboration.service;

import org.dromara.agentoa.collaboration.domain.bo.CollaborationCommentBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskPageQuery;
import org.dromara.agentoa.collaboration.domain.bo.TaskProgressBo;
import org.dromara.agentoa.collaboration.domain.bo.TaskStatusBo;
import org.dromara.agentoa.collaboration.domain.vo.BoardVo;
import org.dromara.agentoa.collaboration.domain.vo.TaskActivityVo;
import org.dromara.agentoa.collaboration.domain.vo.CollabTaskVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/** 任务服务（docs/05 9.3 / docs/17 第 5 步）：状态机、协作者、评论与活动记录。 */
public interface ITaskService {

    PageVo<CollabTaskVo> list(TaskPageQuery query, Long actorUserId);

    CollabTaskVo detail(Long id, Long actorUserId);

    CollabTaskVo create(TaskBo bo, Long actorUserId);

    CollabTaskVo update(Long id, TaskBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);

    CollabTaskVo changeStatus(Long id, TaskStatusBo bo, Long actorUserId);

    CollabTaskVo changeProgress(Long id, TaskProgressBo bo, Long actorUserId);

    TaskActivityVo comment(Long id, CollaborationCommentBo bo, Long actorUserId);

    List<TaskActivityVo> activities(Long id, Long actorUserId);

    BoardVo board(Long actorUserId);

    List<CollabTaskVo.Member> members(Long id, Long actorUserId);

    CollabTaskVo.Member addMember(Long id, String userId, Long actorUserId);

    void removeMember(Long id, String userId, Long actorUserId);
}
