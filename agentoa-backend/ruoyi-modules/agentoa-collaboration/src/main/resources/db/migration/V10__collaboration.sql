-- AgentOA module 7: calendar events, meeting rooms, room bookings and tasks.
-- API contract: docs/05-api-spec.md section 9. Tables follow docs/17 step 1 with the V4 `oa_`
-- prefix convention (docs/04 `cl_*` drafts renamed). Times are stored as UTC DATETIME.

-- ---------------------------------------------------------------- calendar events

CREATE TABLE oa_calendar_event (
  id             BIGINT        NOT NULL                   COMMENT '日程ID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  lock_version   INT           NOT NULL DEFAULT 0         COMMENT '乐观锁版本',
  title          VARCHAR(255)  NOT NULL                   COMMENT '标题',
  description    TEXT          DEFAULT NULL               COMMENT '描述',
  event_type     TINYINT       NOT NULL DEFAULT 1         COMMENT '类型（1日程 2会议）',
  start_time     DATETIME      NOT NULL                   COMMENT '开始时间（UTC）',
  end_time       DATETIME      NOT NULL                   COMMENT '结束时间（UTC）',
  is_all_day     TINYINT       NOT NULL DEFAULT 0         COMMENT '是否全天',
  location       VARCHAR(255)  DEFAULT NULL               COMMENT '地点',
  organizer_id   BIGINT        NOT NULL                   COMMENT '组织者账号ID',
  room_id        BIGINT        DEFAULT NULL               COMMENT '会议室ID',
  visibility     TINYINT       NOT NULL DEFAULT 2         COMMENT '可见范围（1私有 2参与人 3部门 4全员）',
  remind_minutes INT           DEFAULT 15                 COMMENT '提醒时间（分钟）',
  repeat_rule    VARCHAR(64)   DEFAULT NULL               COMMENT '重复规则（P1，未启用）',
  status         TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1正常 2已取消）',
  create_by      BIGINT        DEFAULT NULL               COMMENT '创建人',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT        DEFAULT NULL               COMMENT '更新人',
  update_time    DATETIME      DEFAULT NULL               COMMENT '更新时间',
  del_flag       TINYINT       NOT NULL DEFAULT 0         COMMENT '删除标记（0正常 1已删除）',
  PRIMARY KEY (id),
  KEY idx_event_organizer (organizer_id),
  KEY idx_event_start (start_time, end_time),
  KEY idx_event_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程/会议（参与人与可见范围）';

CREATE TABLE oa_calendar_attendee (
  event_id       BIGINT      NOT NULL COMMENT '日程ID',
  user_id        BIGINT      NOT NULL COMMENT '参与人账号ID',
  response_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '响应状态（PENDING/ACCEPTED/REJECTED）',
  create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
  update_time    DATETIME    DEFAULT NULL COMMENT '响应时间',
  PRIMARY KEY (event_id, user_id),
  KEY idx_attendee_user (user_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程参与人和邀请状态';

-- ---------------------------------------------------------------- meeting rooms and bookings

CREATE TABLE oa_meeting_room (
  id          BIGINT        NOT NULL                   COMMENT '会议室ID',
  tenant_id   VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  name        VARCHAR(64)   NOT NULL                   COMMENT '名称',
  location    VARCHAR(255)  DEFAULT NULL               COMMENT '位置',
  capacity    INT           DEFAULT NULL               COMMENT '容纳人数',
  equipment   VARCHAR(255)  DEFAULT NULL               COMMENT '设备（投影/视频/白板）',
  status      TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1可用 2维护中）',
  create_by   BIGINT        DEFAULT NULL               COMMENT '创建人',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT        DEFAULT NULL               COMMENT '更新人',
  update_time DATETIME      DEFAULT NULL               COMMENT '更新时间',
  del_flag    TINYINT       NOT NULL DEFAULT 0         COMMENT '删除标记（0正常 1已删除）',
  PRIMARY KEY (id),
  KEY idx_room_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室';

CREATE TABLE oa_room_booking (
  id           BIGINT       NOT NULL                   COMMENT '预约ID',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  lock_version INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本',
  room_id      BIGINT       NOT NULL                   COMMENT '会议室ID',
  event_id     BIGINT       DEFAULT NULL               COMMENT '关联日程ID（与日程一一对应）',
  title        VARCHAR(255) NOT NULL                   COMMENT '主题',
  booker_id    BIGINT       NOT NULL                   COMMENT '预订人账号ID',
  start_time   DATETIME     NOT NULL                   COMMENT '开始时间（UTC）',
  end_time     DATETIME     NOT NULL                   COMMENT '结束时间（UTC）',
  checkin_time DATETIME     DEFAULT NULL               COMMENT '签到时间',
  status       TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1预订 2已签到 3已取消 4已释放）',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_booking_room_time (room_id, status, start_time, end_time),
  UNIQUE KEY uk_booking_event (event_id),
  KEY idx_booking_booker (booker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室预约（重叠由行锁拒绝）';

-- ---------------------------------------------------------------- tasks

CREATE TABLE oa_task (
  id            BIGINT       NOT NULL                   COMMENT '任务ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  lock_version  INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本',
  title         VARCHAR(255) NOT NULL                   COMMENT '标题',
  description   TEXT         DEFAULT NULL               COMMENT '描述',
  parent_id     BIGINT       DEFAULT NULL               COMMENT '父任务ID',
  assigner_id   BIGINT       NOT NULL                   COMMENT '指派人账号ID',
  assignee_id   BIGINT       NOT NULL                   COMMENT '负责人账号ID',
  priority      TINYINT      NOT NULL DEFAULT 2         COMMENT '优先级（1P0 2P1 3P2 4P3）',
  status        TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1待办 2进行中 3已阻塞 4已完成 5已取消）',
  start_date    DATE         DEFAULT NULL               COMMENT '开始日期',
  due_date      DATE         DEFAULT NULL               COMMENT '截止日期',
  completed_time DATETIME    DEFAULT NULL               COMMENT '完成时间',
  progress      INT          NOT NULL DEFAULT 0         COMMENT '进度（0-100）',
  tags          VARCHAR(255) DEFAULT NULL               COMMENT '标签',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建人',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新人',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  del_flag      TINYINT      NOT NULL DEFAULT 0         COMMENT '删除标记（0正常 1已删除）',
  PRIMARY KEY (id),
  KEY idx_task_assignee (assignee_id, status),
  KEY idx_task_status (status),
  KEY idx_task_due (due_date),
  KEY idx_task_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务';

CREATE TABLE oa_task_member (
  task_id     BIGINT   NOT NULL COMMENT '任务ID',
  user_id     BIGINT   NOT NULL COMMENT '协作者账号ID',
  create_by   BIGINT   DEFAULT NULL COMMENT '添加人',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  PRIMARY KEY (task_id, user_id),
  KEY idx_task_member_user (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务协作者';

CREATE TABLE oa_task_activity (
  id            BIGINT       NOT NULL                   COMMENT '活动ID',
  task_id       BIGINT       NOT NULL                   COMMENT '任务ID',
  activity_type TINYINT      NOT NULL                   COMMENT '类型（1创建 2状态变更 3进度 4评论 5成员变更）',
  content       TEXT         DEFAULT NULL               COMMENT '内容',
  operator_id   BIGINT       NOT NULL                   COMMENT '操作人账号ID',
  mention_ids   VARCHAR(500) DEFAULT NULL               COMMENT '被@账号ID',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  del_flag      TINYINT      NOT NULL DEFAULT 0         COMMENT '删除标记（0正常 1已删除）',
  PRIMARY KEY (id),
  KEY idx_task_activity (task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务活动与评论';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(90, '000000', '日程类型', 'cl_event_type', 100, 1, sysdate(), null, null, '日程/会议');
INSERT INTO sys_dict_data VALUES(900, '000000', 1, '日程', '1', 'cl_event_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(901, '000000', 2, '会议', '2', 'cl_event_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(91, '000000', '日程可见范围', 'cl_visibility', 100, 1, sysdate(), null, null, '日程可见范围');
INSERT INTO sys_dict_data VALUES(910, '000000', 1, '私有', '1', 'cl_visibility', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(911, '000000', 2, '参与人', '2', 'cl_visibility', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(912, '000000', 3, '本部门', '3', 'cl_visibility', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(913, '000000', 4, '全员', '4', 'cl_visibility', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(92, '000000', '会议室状态', 'cl_room_status', 100, 1, sysdate(), null, null, '会议室状态');
INSERT INTO sys_dict_data VALUES(920, '000000', 1, '可用', '1', 'cl_room_status', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(921, '000000', 2, '维护中', '2', 'cl_room_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(93, '000000', '预约状态', 'cl_booking_status', 100, 1, sysdate(), null, null, '会议室预约状态');
INSERT INTO sys_dict_data VALUES(930, '000000', 1, '已预订', '1', 'cl_booking_status', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(931, '000000', 2, '已签到', '2', 'cl_booking_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(932, '000000', 3, '已取消', '3', 'cl_booking_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(933, '000000', 4, '已释放', '4', 'cl_booking_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(94, '000000', '任务状态', 'cl_task_status', 100, 1, sysdate(), null, null, '任务生命周期');
INSERT INTO sys_dict_data VALUES(940, '000000', 1, '待办', '1', 'cl_task_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(941, '000000', 2, '进行中', '2', 'cl_task_status', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(942, '000000', 3, '已阻塞', '3', 'cl_task_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(943, '000000', 4, '已完成', '4', 'cl_task_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(944, '000000', 5, '已取消', '5', 'cl_task_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(95, '000000', '任务优先级', 'cl_task_priority', 100, 1, sysdate(), null, null, '任务优先级');
INSERT INTO sys_dict_data VALUES(950, '000000', 1, 'P0', '1', 'cl_task_priority', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(951, '000000', 2, 'P1', '2', 'cl_task_priority', '', 'warning', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(952, '000000', 3, 'P2', '3', 'cl_task_priority', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(953, '000000', 4, 'P3', '4', 'cl_task_priority', '', 'info', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- menus and buttons (docs/05 section 9)

INSERT INTO sys_menu VALUES(2600, '日程协作', 0, 12, 'collaboration', null, '', 1, 0, 'M', '0', '0', '', 'date', 100, 1, sysdate(), null, null, '日程、会议室与任务目录');
INSERT INTO sys_menu VALUES(2601, '我的日程', 2600, 1, 'event', 'calendar/event/index', '', 1, 0, 'C', '0', '0', '', 'date-range', 100, 1, sysdate(), null, null, '日视图与邀请响应');
INSERT INTO sys_menu VALUES(2602, '会议室预约', 2600, 2, 'room', 'calendar/room/index', '', 1, 0, 'C', '0', '0', '', 'company', 100, 1, sysdate(), null, null, '会议室、预约、签到与取消');
INSERT INTO sys_menu VALUES(2603, '我的任务', 2600, 3, 'task', 'task/mine/index', '', 1, 0, 'C', '0', '0', '', 'my-task', 100, 1, sysdate(), null, null, '任务、协作者与活动');
INSERT INTO sys_menu VALUES(2610, '日程新建', 2601, 1, '', '', '', 1, 0, 'F', '0', '0', 'cl:event:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2611, '日程修改', 2601, 2, '', '', '', 1, 0, 'F', '0', '0', 'cl:event:edit', '#', 100, 1, sysdate(), null, null, '仅组织者');
INSERT INTO sys_menu VALUES(2612, '日程删除', 2601, 3, '', '', '', 1, 0, 'F', '0', '0', 'cl:event:remove', '#', 100, 1, sysdate(), null, null, '仅组织者');
INSERT INTO sys_menu VALUES(2613, '会议室新增', 2602, 1, '', '', '', 1, 0, 'F', '0', '0', 'cl:room:add', '#', 100, 1, sysdate(), null, null, 'HR 维护');
INSERT INTO sys_menu VALUES(2614, '会议室修改', 2602, 2, '', '', '', 1, 0, 'F', '0', '0', 'cl:room:edit', '#', 100, 1, sysdate(), null, null, 'HR 维护');
INSERT INTO sys_menu VALUES(2615, '会议室删除', 2602, 3, '', '', '', 1, 0, 'F', '0', '0', 'cl:room:remove', '#', 100, 1, sysdate(), null, null, 'HR 维护');
INSERT INTO sys_menu VALUES(2616, '会议室预约', 2602, 4, '', '', '', 1, 0, 'F', '0', '0', 'cl:room:book', '#', 100, 1, sysdate(), null, null, '预约、签到与取消');
INSERT INTO sys_menu VALUES(2617, '任务新建', 2603, 1, '', '', '', 1, 0, 'F', '0', '0', 'cl:task:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2618, '任务修改', 2603, 2, '', '', '', 1, 0, 'F', '0', '0', 'cl:task:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2619, '任务删除', 2603, 3, '', '', '', 1, 0, 'F', '0', '0', 'cl:task:remove', '#', 100, 1, sysdate(), null, null, '');

-- Role-menu matrix: every business role manages own events/tasks/bookings; HR maintains rooms.
INSERT INTO sys_role_menu VALUES(20, 2600);
INSERT INTO sys_role_menu VALUES(20, 2601);
INSERT INTO sys_role_menu VALUES(20, 2602);
INSERT INTO sys_role_menu VALUES(20, 2603);
INSERT INTO sys_role_menu VALUES(20, 2610);
INSERT INTO sys_role_menu VALUES(20, 2611);
INSERT INTO sys_role_menu VALUES(20, 2612);
INSERT INTO sys_role_menu VALUES(20, 2613);
INSERT INTO sys_role_menu VALUES(20, 2614);
INSERT INTO sys_role_menu VALUES(20, 2615);
INSERT INTO sys_role_menu VALUES(20, 2616);
INSERT INTO sys_role_menu VALUES(20, 2617);
INSERT INTO sys_role_menu VALUES(20, 2618);
INSERT INTO sys_role_menu VALUES(20, 2619);
INSERT INTO sys_role_menu VALUES(21, 2600);
INSERT INTO sys_role_menu VALUES(21, 2601);
INSERT INTO sys_role_menu VALUES(21, 2602);
INSERT INTO sys_role_menu VALUES(21, 2603);
INSERT INTO sys_role_menu VALUES(21, 2610);
INSERT INTO sys_role_menu VALUES(21, 2611);
INSERT INTO sys_role_menu VALUES(21, 2612);
INSERT INTO sys_role_menu VALUES(21, 2616);
INSERT INTO sys_role_menu VALUES(21, 2617);
INSERT INTO sys_role_menu VALUES(21, 2618);
INSERT INTO sys_role_menu VALUES(21, 2619);
INSERT INTO sys_role_menu VALUES(22, 2600);
INSERT INTO sys_role_menu VALUES(22, 2601);
INSERT INTO sys_role_menu VALUES(22, 2602);
INSERT INTO sys_role_menu VALUES(22, 2603);
INSERT INTO sys_role_menu VALUES(22, 2610);
INSERT INTO sys_role_menu VALUES(22, 2611);
INSERT INTO sys_role_menu VALUES(22, 2612);
INSERT INTO sys_role_menu VALUES(22, 2616);
INSERT INTO sys_role_menu VALUES(22, 2617);
INSERT INTO sys_role_menu VALUES(22, 2618);
INSERT INTO sys_role_menu VALUES(22, 2619);
INSERT INTO sys_role_menu VALUES(23, 2600);
INSERT INTO sys_role_menu VALUES(23, 2601);
INSERT INTO sys_role_menu VALUES(23, 2602);
INSERT INTO sys_role_menu VALUES(23, 2603);
INSERT INTO sys_role_menu VALUES(23, 2610);
INSERT INTO sys_role_menu VALUES(23, 2611);
INSERT INTO sys_role_menu VALUES(23, 2612);
INSERT INTO sys_role_menu VALUES(23, 2616);
INSERT INTO sys_role_menu VALUES(23, 2617);
INSERT INTO sys_role_menu VALUES(23, 2618);
INSERT INTO sys_role_menu VALUES(23, 2619);
INSERT INTO sys_role_menu VALUES(24, 2600);
INSERT INTO sys_role_menu VALUES(24, 2601);
INSERT INTO sys_role_menu VALUES(24, 2602);
INSERT INTO sys_role_menu VALUES(24, 2603);
INSERT INTO sys_role_menu VALUES(24, 2610);
INSERT INTO sys_role_menu VALUES(24, 2611);
INSERT INTO sys_role_menu VALUES(24, 2612);
INSERT INTO sys_role_menu VALUES(24, 2616);
INSERT INTO sys_role_menu VALUES(24, 2617);
INSERT INTO sys_role_menu VALUES(24, 2618);
INSERT INTO sys_role_menu VALUES(24, 2619);
INSERT INTO sys_role_menu VALUES(25, 2600);
INSERT INTO sys_role_menu VALUES(25, 2601);
INSERT INTO sys_role_menu VALUES(25, 2602);
INSERT INTO sys_role_menu VALUES(25, 2603);
INSERT INTO sys_role_menu VALUES(25, 2610);
INSERT INTO sys_role_menu VALUES(25, 2611);
INSERT INTO sys_role_menu VALUES(25, 2612);
INSERT INTO sys_role_menu VALUES(25, 2616);
INSERT INTO sys_role_menu VALUES(25, 2617);
INSERT INTO sys_role_menu VALUES(25, 2618);
INSERT INTO sys_role_menu VALUES(25, 2619);
