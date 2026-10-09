-- AgentOA module 5: announcements, audience snapshots, message center preferences and outbox redelivery.
-- API contract: docs/05-api-spec.md section 7. Reuses V2 nc_message / sys_outbox (docs/15 step 1) and
-- extends them with business references and redelivery bookkeeping.

-- ---------------------------------------------------------------- announcements

CREATE TABLE oa_announcement (
  id               BIGINT        NOT NULL                   COMMENT '公告ID',
  tenant_id        VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  title            VARCHAR(255)  NOT NULL                   COMMENT '标题',
  content          LONGTEXT      NOT NULL                   COMMENT '内容（富文本）',
  notice_type      VARCHAR(32)   DEFAULT NULL               COMMENT '公告类型（company/department/hr/admin）',
  publisher_id     BIGINT        NOT NULL                   COMMENT '发布人账号ID',
  publish_time     DATETIME      DEFAULT NULL               COMMENT '发布时间',
  effective_start  DATETIME      DEFAULT NULL               COMMENT '生效开始',
  effective_end    DATETIME      DEFAULT NULL               COMMENT '生效结束',
  scope_type       TINYINT       NOT NULL DEFAULT 1         COMMENT '范围（1全员 2部门 3角色 4指定人）',
  scope_values     VARCHAR(2000) DEFAULT NULL               COMMENT '范围值（部门ID/角色key/用户ID，逗号分隔）',
  is_top           TINYINT       NOT NULL DEFAULT 0         COMMENT '是否置顶',
  is_popup         TINYINT       NOT NULL DEFAULT 0         COMMENT '是否弹窗',
  status           TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2已发布 3已撤回 4已归档）',
  read_count       INT           NOT NULL DEFAULT 0         COMMENT '已读人数',
  attachments      JSON          DEFAULT NULL               COMMENT '附件列表（发布时冻结）',
  create_dept      BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_announcement_status (status, publish_time),
  KEY idx_announcement_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告（发布时冻结内容与受众快照）';

CREATE TABLE oa_announcement_audience (
  notice_id   BIGINT   NOT NULL COMMENT '公告ID',
  user_id     BIGINT   NOT NULL COMMENT '接收账号ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
  PRIMARY KEY (notice_id, user_id),
  KEY idx_announcement_audience_user (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告发布时受众快照';

CREATE TABLE oa_announcement_read (
  id         BIGINT   NOT NULL COMMENT '已读记录ID',
  notice_id  BIGINT   NOT NULL COMMENT '公告ID',
  user_id    BIGINT   NOT NULL COMMENT '账号ID',
  read_time  DATETIME NOT NULL COMMENT '已读时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_announcement_read (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读记录';

-- ---------------------------------------------------------------- notification preferences

CREATE TABLE oa_notification_preference (
  user_id     BIGINT      NOT NULL COMMENT '账号ID',
  msg_type    VARCHAR(32) NOT NULL COMMENT '消息类型（TODO/NOTICE/MENTION/SYSTEM）',
  enabled     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否推送提醒（1是 0否；消息仍持久化可补拉）',
  update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (user_id, msg_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知偏好（仅抑制实时提醒，不影响持久化与补拉）';

-- ---------------------------------------------------------------- extend V2 message and outbox

ALTER TABLE nc_message ADD COLUMN biz_type VARCHAR(32) DEFAULT NULL COMMENT '业务类型';
ALTER TABLE nc_message ADD COLUMN biz_id BIGINT DEFAULT NULL COMMENT '业务ID';
ALTER TABLE nc_message ADD COLUMN del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记（0在 1已删除）';

ALTER TABLE sys_outbox ADD COLUMN redeliver_count INT NOT NULL DEFAULT 0 COMMENT '人工重投次数';
ALTER TABLE sys_outbox ADD COLUMN redelivered_by BIGINT DEFAULT NULL COMMENT '重投操作账号ID';
ALTER TABLE sys_outbox ADD COLUMN redelivered_time DATETIME DEFAULT NULL COMMENT '重投时间';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(70, '000000', '公告类型', 'nc_notice_type', 100, 1, sysdate(), null, null, '公告类型');
INSERT INTO sys_dict_data VALUES(700, '000000', 1, '公司公告', 'company', 'nc_notice_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(701, '000000', 2, '部门公告', 'department', 'nc_notice_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(702, '000000', 3, '人事公告', 'hr', 'nc_notice_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(703, '000000', 4, '行政公告', 'admin', 'nc_notice_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(71, '000000', '公告范围', 'nc_scope_type', 100, 1, sysdate(), null, null, '公告受众范围');
INSERT INTO sys_dict_data VALUES(710, '000000', 1, '全员', '1', 'nc_scope_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(711, '000000', 2, '部门', '2', 'nc_scope_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(712, '000000', 3, '角色', '3', 'nc_scope_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(713, '000000', 4, '指定人', '4', 'nc_scope_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(72, '000000', '消息类型', 'nc_msg_type', 100, 1, sysdate(), null, null, '站内信类型');
INSERT INTO sys_dict_data VALUES(720, '000000', 1, '待办', 'TODO', 'nc_msg_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(721, '000000', 2, '公告', 'NOTICE', 'nc_msg_type', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(722, '000000', 3, '提及', 'MENTION', 'nc_msg_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(723, '000000', 4, '系统', 'SYSTEM', 'nc_msg_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(73, '000000', '公告状态', 'nc_notice_status', 100, 1, sysdate(), null, null, '公告生命周期');
INSERT INTO sys_dict_data VALUES(730, '000000', 1, '草稿', '1', 'nc_notice_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(731, '000000', 2, '已发布', '2', 'nc_notice_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(732, '000000', 3, '已撤回', '3', 'nc_notice_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(733, '000000', 4, '已归档', '4', 'nc_notice_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'P1');

-- ---------------------------------------------------------------- menus and buttons (permission strings follow docs/05 section 7 scope rules)

INSERT INTO sys_menu VALUES(2400, '公告通知', 0, 10, 'notice', null, '', 1, 0, 'M', '0', '0', '', 'message', 100, 1, sysdate(), null, null, '公告通知目录');
INSERT INTO sys_menu VALUES(2401, '公告管理', 2400, 1, 'announcement', 'notice/announcement/index', '', 1, 0, 'C', '0', '0', '', 'documentation', 100, 1, sysdate(), null, null, '公告列表、发布与撤回');
INSERT INTO sys_menu VALUES(2402, '消息中心', 2400, 2, 'message', 'notice/message/index', '', 1, 0, 'C', '0', '0', '', 'list', 100, 1, sysdate(), null, null, '站内信、已读与补拉');
INSERT INTO sys_menu VALUES(2410, '公告发布', 2401, 1, '', '', '', 1, 0, 'F', '0', '0', 'nt:notice:add', '#', 100, 1, sysdate(), null, null, '创建并发布公告');
INSERT INTO sys_menu VALUES(2411, '公告修改', 2401, 2, '', '', '', 1, 0, 'F', '0', '0', 'nt:notice:edit', '#', 100, 1, sysdate(), null, null, '仅草稿可修改');
INSERT INTO sys_menu VALUES(2412, '公告撤回', 2401, 3, '', '', '', 1, 0, 'F', '0', '0', 'nt:notice:recall', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2413, '公告删除', 2401, 4, '', '', '', 1, 0, 'F', '0', '0', 'nt:notice:remove', '#', 100, 1, sysdate(), null, null, '仅草稿可删除');
INSERT INTO sys_menu VALUES(2414, '已读统计', 2401, 5, '', '', '', 1, 0, 'F', '0', '0', 'nt:notice:read', '#', 100, 1, sysdate(), null, null, '受众已读/未读列表');
INSERT INTO sys_menu VALUES(2415, '消息事件查询', 2402, 1, '', '', '', 1, 0, 'F', '0', '0', 'nt:outbox:list', '#', 100, 1, sysdate(), null, null, '失败事件查询');
INSERT INTO sys_menu VALUES(2416, '消息重投', 2402, 2, '', '', '', 1, 0, 'F', '0', '0', 'nt:outbox:redeliver', '#', 100, 1, sysdate(), null, null, '失败事件人工重投');

-- Role-menu matrix: HR publishes announcements, all roles read announcements and their inbox.
INSERT INTO sys_role_menu VALUES(20, 2400);
INSERT INTO sys_role_menu VALUES(20, 2401);
INSERT INTO sys_role_menu VALUES(20, 2402);
INSERT INTO sys_role_menu VALUES(20, 2410);
INSERT INTO sys_role_menu VALUES(20, 2411);
INSERT INTO sys_role_menu VALUES(20, 2412);
INSERT INTO sys_role_menu VALUES(20, 2413);
INSERT INTO sys_role_menu VALUES(20, 2414);
INSERT INTO sys_role_menu VALUES(20, 2415);
INSERT INTO sys_role_menu VALUES(20, 2416);
INSERT INTO sys_role_menu VALUES(21, 2400);
INSERT INTO sys_role_menu VALUES(21, 2401);
INSERT INTO sys_role_menu VALUES(21, 2402);
INSERT INTO sys_role_menu VALUES(22, 2400);
INSERT INTO sys_role_menu VALUES(22, 2401);
INSERT INTO sys_role_menu VALUES(22, 2402);
INSERT INTO sys_role_menu VALUES(23, 2400);
INSERT INTO sys_role_menu VALUES(23, 2401);
INSERT INTO sys_role_menu VALUES(23, 2402);
INSERT INTO sys_role_menu VALUES(24, 2400);
INSERT INTO sys_role_menu VALUES(24, 2401);
INSERT INTO sys_role_menu VALUES(24, 2402);
INSERT INTO sys_role_menu VALUES(25, 2400);
INSERT INTO sys_role_menu VALUES(25, 2401);
INSERT INTO sys_role_menu VALUES(25, 2402);
