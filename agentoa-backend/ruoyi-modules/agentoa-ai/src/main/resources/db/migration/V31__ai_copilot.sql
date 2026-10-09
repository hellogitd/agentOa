-- AgentOA module 10 (M4): business copilot - scene config and async generation tasks.
-- API contract: docs/21-module-plan-ai.md section 6. AI 生成内容仅供参考，人工确认后才进入业务流程。
-- 业务数据只取只读摘要（字段与意见摘要），AI 不直接修改业务数据（docs/21 §6）。

-- ---------------------------------------------------------------- scene config, copilot tasks

CREATE TABLE oa_ai_copilot_config (
  id                 BIGINT        NOT NULL                   COMMENT '配置ID',
  tenant_id          VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  scene_code         VARCHAR(64)   NOT NULL                   COMMENT '场景码（ai_copilot_scene）',
  scene_name         VARCHAR(128)  NOT NULL                   COMMENT '场景名称',
  enabled            TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  model_id           BIGINT        DEFAULT NULL               COMMENT '生成模型ID（空取全局默认）',
  prompt_template_id BIGINT        DEFAULT NULL               COMMENT '提示词模板ID',
  create_dept        BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_copilot_scene (scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 业务助手场景配置（每场景独立开关/模型/提示词）';

CREATE TABLE oa_ai_copilot_task (
  id            BIGINT        NOT NULL                   COMMENT '任务ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  scene_code    VARCHAR(64)   NOT NULL                   COMMENT '场景码（ai_copilot_scene）',
  biz_type      VARCHAR(32)   DEFAULT NULL               COMMENT '业务类型（如 flow_instance/notice/calendar_event）',
  biz_id        BIGINT        DEFAULT NULL               COMMENT '业务ID',
  user_id       BIGINT        NOT NULL                   COMMENT '发起账号ID',
  input_ref     TEXT          DEFAULT NULL               COMMENT '输入上下文（只读摘要，重试时复用）',
  output        LONGTEXT      DEFAULT NULL               COMMENT '生成结果（AI 生成内容仅供参考）',
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT '状态（ai_copilot_task）',
  error_msg     VARCHAR(500)  DEFAULT NULL               COMMENT '失败原因',
  prompt_tokens INT           NOT NULL DEFAULT 0         COMMENT '输入 token',
  total_tokens  INT           NOT NULL DEFAULT 0         COMMENT '合计 token',
  finish_time   DATETIME      DEFAULT NULL               COMMENT '完成时间',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_copilot_task_user (user_id, status, create_time),
  KEY idx_ai_copilot_task_biz (scene_code, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 业务助手异步任务（长摘要/报表生成）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(148, '000000', 'AI 助手任务状态', 'ai_copilot_task', 100, 1, sysdate(), null, null, '业务助手异步任务状态');
INSERT INTO sys_dict_data VALUES(1480, '000000', 1,  '待处理', 'pending', 'ai_copilot_task', '', 'info',    'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1481, '000000', 2,  '生成中', 'running', 'ai_copilot_task', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1482, '000000', 3,  '已完成', 'done',    'ai_copilot_task', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1483, '000000', 4,  '失败',   'failed',  'ai_copilot_task', '', 'danger',  'N', 100, 1, sysdate(), null, null, '可重试');

-- ---------------------------------------------------------------- built-in scene prompts（docs/21 §6.1）

INSERT INTO oa_ai_prompt_template VALUES(5006, '000000', 'copilot_approve_summary', '审批摘要', '办公', '你是企业办公助手，负责为审批单生成摘要。请根据给出的审批表单字段、历史审批意见与耗时，输出「摘要 + 风险提示 + 建议」三段。只做总结与提示，不要替用户做出审批结论。AI 生成内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5007, '000000', 'copilot_report_insight', '报表解读', '办公', '你是企业经营分析师。请根据给出的报表指标（含同比/环比、对比值、异常项）输出「关键结论、异常与原因推测、建议行动」要点。不得编造未给出的数据。AI 生成内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5008, '000000', 'copilot_notice_draft', '通知起草', '办公', '你是企业行政文秘。请根据给出的要点起草通知正文，结构为标题、正文（背景/事项/要求）、落款。语言正式、庄重、简洁，避免夸张与口语化。AI 生成内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5009, '000000', 'copilot_form_suggest', '表单填写建议', '办公', '你是企业办公助手。请根据表单字段定义与用户意图，给出每个字段的建议取值与理由，以 JSON 数组输出：[{"key":"字段名","suggestion":"建议值","reason":"理由"}]。只给建议，不要替用户提交。AI 生成内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5010, '000000', 'copilot_minutes', '会议纪要', '办公', '你是会议纪要整理助手。请把给定的会议记录/发言转写成结构化纪要草稿，包含会议主题、时间地点、参会人、议题、讨论要点、决议与待办。只做草稿，需人工确认后存入日程备注。AI 生成内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');

INSERT INTO oa_ai_copilot_config VALUES(6001, '000000', 'approve-summary', '审批摘要', 1, null, 5006, 100, 1, sysdate(), null, null, '内置场景');
INSERT INTO oa_ai_copilot_config VALUES(6002, '000000', 'report-insight', '报表解读', 1, null, 5007, 100, 1, sysdate(), null, null, '内置场景');
INSERT INTO oa_ai_copilot_config VALUES(6003, '000000', 'notice-draft', '通知起草', 1, null, 5008, 100, 1, sysdate(), null, null, '内置场景');
INSERT INTO oa_ai_copilot_config VALUES(6004, '000000', 'form-suggest', '表单填写建议', 1, null, 5009, 100, 1, sysdate(), null, null, '内置场景');
INSERT INTO oa_ai_copilot_config VALUES(6005, '000000', 'minutes', '会议纪要', 1, null, 5010, 100, 1, sysdate(), null, null, '内置场景');

-- ---------------------------------------------------------------- menus and buttons (docs/21 §2.4)

INSERT INTO sys_menu VALUES(3003, '业务助手', 3000, 3, 'copilot', 'ai/copilot/index', '', 1, 0, 'C', '0', '0', '', 'magic-stick', 100, 1, sysdate(), null, null, '业务场景 Copilot（AI 生成内容仅供参考）');
INSERT INTO sys_menu VALUES(3012, '助手使用', 3003, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:copilot:use', '#', 100, 1, sysdate(), null, null, '使用业务助手');
INSERT INTO sys_menu VALUES(3013, '场景配置', 3003, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:copilot:config', '#', 100, 1, sysdate(), null, null, '启停/换模型/换模板（管理员）');

-- 全员可使用业务助手（docs/21 §6）；场景配置仅管理员。
INSERT INTO sys_role_menu VALUES(20, 3003);
INSERT INTO sys_role_menu VALUES(20, 3012);
INSERT INTO sys_role_menu VALUES(21, 3003);
INSERT INTO sys_role_menu VALUES(21, 3012);
INSERT INTO sys_role_menu VALUES(22, 3003);
INSERT INTO sys_role_menu VALUES(22, 3012);
INSERT INTO sys_role_menu VALUES(23, 3003);
INSERT INTO sys_role_menu VALUES(23, 3012);
INSERT INTO sys_role_menu VALUES(24, 3003);
INSERT INTO sys_role_menu VALUES(24, 3012);
INSERT INTO sys_role_menu VALUES(25, 3003);
INSERT INTO sys_role_menu VALUES(25, 3012);
INSERT INTO sys_role_menu VALUES(26, 3003);
INSERT INTO sys_role_menu VALUES(26, 3012);
INSERT INTO sys_role_menu VALUES(26, 3013);
INSERT INTO sys_role_menu VALUES(27, 3003);
INSERT INTO sys_role_menu VALUES(27, 3012);
