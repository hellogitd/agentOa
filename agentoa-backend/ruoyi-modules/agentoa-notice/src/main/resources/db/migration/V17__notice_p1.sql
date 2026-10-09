-- P1 批次二 · 交付包 C（NC-04 通知模板与定时推送）。
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 15 节）。

-- ---------------------------------------------------------------- notice templates

CREATE TABLE oa_notice_template (
  id            BIGINT       NOT NULL                   COMMENT '模板ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  template_code VARCHAR(64)  NOT NULL                   COMMENT '模板编码（唯一，{var} 占位符模板）',
  name          VARCHAR(128) NOT NULL                   COMMENT '模板名称',
  title_tpl     VARCHAR(255) NOT NULL                   COMMENT '标题模板',
  content_tpl   TEXT         NOT NULL                   COMMENT '内容模板',
  msg_type      VARCHAR(32)  NOT NULL DEFAULT 'NOTICE'  COMMENT '消息类型（TODO/NOTICE/SYSTEM）',
  vars_json     VARCHAR(2000) DEFAULT NULL              COMMENT '变量声明（JSON 数组，白名单）',
  status        TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1启用 2停用）',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建人',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新人',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_notice_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板（受控 {var} 占位符，禁表达式）';

-- ---------------------------------------------------------------- scheduled push

CREATE TABLE oa_scheduled_push (
  id              BIGINT        NOT NULL                   COMMENT '推送任务ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  name            VARCHAR(128)  NOT NULL                   COMMENT '任务名称',
  push_type       TINYINT       NOT NULL                   COMMENT '推送类型（1定时公告 2模板推送）',
  announcement_id BIGINT        DEFAULT NULL               COMMENT '定时公告ID（类型1，到点调 publish）',
  template_id     BIGINT        DEFAULT NULL               COMMENT '通知模板ID（类型2）',
  scope_type      TINYINT       NOT NULL DEFAULT 1         COMMENT '受众范围（1全员 2部门 3角色 4指定人，类型2）',
  scope_values    VARCHAR(2000) DEFAULT NULL               COMMENT '范围值（类型2）',
  vars_json       VARCHAR(2000) DEFAULT NULL               COMMENT '模板变量值（类型2，JSON 对象）',
  schedule_type   TINYINT       NOT NULL DEFAULT 1         COMMENT '调度方式（1单次 2周期cron子集）',
  run_at          DATETIME      DEFAULT NULL               COMMENT '单次执行时间（调度1）',
  cron_expr       VARCHAR(64)   DEFAULT NULL               COMMENT '周期表达式（调度2，5字段受限子集）',
  next_run_time   DATETIME      DEFAULT NULL               COMMENT '下次执行时间（NULL=无待执行）',
  last_run_time   DATETIME      DEFAULT NULL               COMMENT '上次执行时间',
  run_count       INT           NOT NULL DEFAULT 0         COMMENT '累计成功执行次数',
  last_error      VARCHAR(500)  DEFAULT NULL               COMMENT '最近执行错误',
  status          TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1启用 2暂停）',
  create_by       BIGINT        DEFAULT NULL               COMMENT '创建人',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       BIGINT        DEFAULT NULL               COMMENT '更新人',
  update_time     DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_scheduled_push_due (status, next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时推送（定时公告/模板推送，DB 租约调度）';

CREATE TABLE oa_scheduled_push_run (
  id             BIGINT       NOT NULL                   COMMENT '执行记录ID',
  push_id        BIGINT       NOT NULL                   COMMENT '推送任务ID',
  event_key      VARCHAR(64)  NOT NULL                   COMMENT 'SCHED-{pushId}-{slot} 幂等去重键',
  slot_time      DATETIME     NOT NULL                   COMMENT '计划执行时间（slot）',
  status         TINYINT      NOT NULL DEFAULT 1         COMMENT '结果（1成功 2失败）',
  receiver_count INT          NOT NULL DEFAULT 0         COMMENT '本次投递人数',
  error          VARCHAR(500) DEFAULT NULL               COMMENT '失败原因',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_push_run_event (event_key),
  KEY idx_push_run_push (push_id, slot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时推送执行记录（幂等去重与执行历史）';

-- ---------------------------------------------------------------- built-in template seeds

INSERT INTO oa_notice_template(id, template_code, name, title_tpl, content_tpl, msg_type, vars_json, status, remark)
VALUES(1, 'onboarding_welcome', '入职欢迎', '欢迎加入 {dept}',
  '{name}，欢迎你加入 {dept}！入职日期：{date}。请登录系统完善个人信息并查阅员工手册。', 'NOTICE',
  '["name","dept","date"]', 1, '内置模板：入职欢迎（NC-04）');
INSERT INTO oa_notice_template(id, template_code, name, title_tpl, content_tpl, msg_type, vars_json, status, remark)
VALUES(2, 'birthday_greeting', '生日祝福', '生日快乐，{name}',
  '今天是 {date}，祝 {name} 生日快乐！感谢你与公司共同成长。', 'NOTICE',
  '["name","date"]', 1, '内置模板：生日祝福（NC-04）');
INSERT INTO oa_notice_template(id, template_code, name, title_tpl, content_tpl, msg_type, vars_json, status, remark)
VALUES(3, 'attendance_anomaly', '考勤异常提醒', '考勤异常提醒（{date}）',
  '{name}，你在 {date} 的考勤存在异常：{detail}。请及时处理或提交补卡申请。', 'NOTICE',
  '["name","date","detail"]', 1, '内置模板：考勤异常提醒（NC-04）');

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(130, '000000', '定时推送类型', 'nc_push_type', 100, 1, sysdate(), null, null, '定时推送任务类型');
INSERT INTO sys_dict_data VALUES(1300, '000000', 1, '定时公告', '1', 'nc_push_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '到点发布草稿公告');
INSERT INTO sys_dict_data VALUES(1301, '000000', 2, '模板推送', '2', 'nc_push_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '按模板渲染并推送');
INSERT INTO sys_dict_type VALUES(131, '000000', '推送调度方式', 'nc_schedule_type', 100, 1, sysdate(), null, null, '定时推送调度方式');
INSERT INTO sys_dict_data VALUES(1310, '000000', 1, '单次', '1', 'nc_schedule_type', '', 'info', 'Y', 100, 1, sysdate(), null, null, 'run_at 一次执行');
INSERT INTO sys_dict_data VALUES(1311, '000000', 2, '周期', '2', 'nc_schedule_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'cron 受限子集');
INSERT INTO sys_dict_type VALUES(132, '000000', '推送任务状态', 'nc_push_status', 100, 1, sysdate(), null, null, '定时推送任务状态');
INSERT INTO sys_dict_data VALUES(1320, '000000', 1, '启用', '1', 'nc_push_status', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1321, '000000', 2, '暂停', '2', 'nc_push_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- menus and buttons (nt:template:* / nt:schedule:*)

INSERT INTO sys_menu VALUES(2420, '通知模板', 2400, 3, 'template', 'notice/template/index', '', 1, 0, 'C', '0', '0', '', 'edit', 100, 1, sysdate(), null, null, '通知模板管理（NC-04）');
INSERT INTO sys_menu VALUES(2421, '定时推送', 2400, 4, 'schedule', 'notice/schedule/index', '', 1, 0, 'C', '0', '0', '', 'time', 100, 1, sysdate(), null, null, '定时公告与模板推送（NC-04）');
INSERT INTO sys_menu VALUES(2422, '模板新增', 2420, 1, '', '', '', 1, 0, 'F', '0', '0', 'nt:template:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2423, '模板修改', 2420, 2, '', '', '', 1, 0, 'F', '0', '0', 'nt:template:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2424, '模板删除', 2420, 3, '', '', '', 1, 0, 'F', '0', '0', 'nt:template:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2425, '模板发送', 2420, 4, '', '', '', 1, 0, 'F', '0', '0', 'nt:template:send', '#', 100, 1, sysdate(), null, null, '按受众渲染并发送');
INSERT INTO sys_menu VALUES(2426, '推送新增', 2421, 1, '', '', '', 1, 0, 'F', '0', '0', 'nt:schedule:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2427, '推送修改', 2421, 2, '', '', '', 1, 0, 'F', '0', '0', 'nt:schedule:edit', '#', 100, 1, sysdate(), null, null, '含暂停/恢复');
INSERT INTO sys_menu VALUES(2428, '推送删除', 2421, 3, '', '', '', 1, 0, 'F', '0', '0', 'nt:schedule:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2429, '推送执行', 2421, 4, '', '', '', 1, 0, 'F', '0', '0', 'nt:schedule:run', '#', 100, 1, sysdate(), null, null, '手动触发到期扫描');

-- Role-menu matrix: HR (role 20) manages templates and pushes; other roles unchanged (员工不可管).
INSERT INTO sys_role_menu VALUES(20, 2420);
INSERT INTO sys_role_menu VALUES(20, 2421);
INSERT INTO sys_role_menu VALUES(20, 2422);
INSERT INTO sys_role_menu VALUES(20, 2423);
INSERT INTO sys_role_menu VALUES(20, 2424);
INSERT INTO sys_role_menu VALUES(20, 2425);
INSERT INTO sys_role_menu VALUES(20, 2426);
INSERT INTO sys_role_menu VALUES(20, 2427);
INSERT INTO sys_role_menu VALUES(20, 2428);
INSERT INTO sys_role_menu VALUES(20, 2429);
