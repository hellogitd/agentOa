-- AgentOA module 10 (M2): AI assistant chat - conversations, messages and prompt templates.
-- API contract: docs/21-module-plan-ai.md section 4. Streaming output is SSE (text/event-stream).

-- ---------------------------------------------------------------- conversations, messages, templates

CREATE TABLE oa_ai_conversation (
  id                 BIGINT        NOT NULL                   COMMENT '会话ID',
  tenant_id          VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id            BIGINT        NOT NULL                   COMMENT '所属账号ID（本人可见）',
  title              VARCHAR(255)  DEFAULT NULL               COMMENT '会话标题（首条消息自动命名）',
  model_id           BIGINT        DEFAULT NULL               COMMENT '绑定模型ID（可切换）',
  prompt_template_id BIGINT        DEFAULT NULL               COMMENT '绑定提示词模板ID',
  status             VARCHAR(16)   NOT NULL DEFAULT 'active'  COMMENT '状态（active/deleted）',
  last_message_time  DATETIME      DEFAULT NULL               COMMENT '最后消息时间',
  create_dept        BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_conversation_user (user_id, status, last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话会话（用户隔离）';

CREATE TABLE oa_ai_message (
  id                BIGINT        NOT NULL                   COMMENT '消息ID',
  tenant_id         VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  conversation_id   BIGINT        NOT NULL                   COMMENT '所属会话ID',
  role              VARCHAR(16)   NOT NULL                   COMMENT '角色（user/assistant/system）',
  content           LONGTEXT      DEFAULT NULL               COMMENT '消息内容',
  attachments       VARCHAR(2000) DEFAULT NULL               COMMENT '附件（JSON：sys_file id 列表）',
  model_id          BIGINT        DEFAULT NULL               COMMENT '生成该消息的模型ID',
  prompt_tokens     INT           NOT NULL DEFAULT 0         COMMENT '输入 token',
  completion_tokens INT           NOT NULL DEFAULT 0         COMMENT '输出 token',
  status            VARCHAR(16)   NOT NULL DEFAULT 'done'    COMMENT '状态（streaming/done/stopped/error）',
  error_code        VARCHAR(64)   DEFAULT NULL               COMMENT '错误码',
  create_dept       BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by         BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time       DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark            VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_message_conv (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话消息（持久化即记忆来源）';

CREATE TABLE oa_ai_prompt_template (
  id          BIGINT        NOT NULL                   COMMENT '模板ID',
  tenant_id   VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  code        VARCHAR(64)   NOT NULL                   COMMENT '模板编码',
  name        VARCHAR(128)  NOT NULL                   COMMENT '模板名称',
  category    VARCHAR(64)   DEFAULT NULL               COMMENT '模板分类',
  content     LONGTEXT      NOT NULL                   COMMENT '系统提示词内容',
  enabled     TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  is_builtin  TINYINT       NOT NULL DEFAULT 0         COMMENT '是否内置（1是 0否）',
  create_dept BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by   BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark      VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_prompt_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 提示词模板（AI 生成内容仅供参考）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(145, '000000', 'AI 消息角色', 'ai_msg_role', 100, 1, sysdate(), null, null, '对话消息角色');
INSERT INTO sys_dict_data VALUES(1450, '000000', 1,  '用户',   'user',      'ai_msg_role', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1451, '000000', 2,  '助手',   'assistant', 'ai_msg_role', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1452, '000000', 3,  '系统',   'system',    'ai_msg_role', '', 'info',    'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- built-in prompt templates

INSERT INTO oa_ai_prompt_template VALUES(5001, '000000', 'official_doc', '公文写作', '写作', '你是政府与企业公文写作助手。请按正式公文的结构（标题、主送、正文、落款）起草，语言庄重、条理清晰、避免口语化表达。输出内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5002, '000000', 'email_polish', '邮件润色', '写作', '你是商务邮件润色助手。请在不改变原意的前提下，使邮件语气更专业、结构更清晰，输出润色后的完整邮件。输出内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5003, '000000', 'translate', '翻译', '写作', '你是专业翻译助手。请在中文与英文之间互译，保留原文格式与术语一致性，必要时给出简短译注。输出内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5004, '000000', 'report_insight', '报表解读', '分析', '你是经营分析助手。请根据给出的指标口径与数值，输出趋势、异常与建议要点，区分事实与推测。输出内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');
INSERT INTO oa_ai_prompt_template VALUES(5005, '000000', 'meeting_minutes', '会议纪要', '办公', '你是会议纪要助手。请把转写或要点整理为纪要草稿（会议主题、时间、参会人、议题、结论、待办）。输出内容仅供参考，请人工核对后使用。', 1, 1, 100, 1, sysdate(), null, null, '内置模板');

-- ---------------------------------------------------------------- menus and buttons (docs/21 §2.4)

INSERT INTO sys_menu VALUES(3000, 'AI 助手', 0, 29, 'ai', null, '', 1, 0, 'M', '0', '0', '', 'chat-dot-round', 100, 1, sysdate(), null, null, 'AI 助手目录');
INSERT INTO sys_menu VALUES(3001, 'AI 对话', 3000, 1, 'chat', 'ai/chat/index', '', 1, 0, 'C', '0', '0', '', 'message', 100, 1, sysdate(), null, null, '多模态对话助手');
INSERT INTO sys_menu VALUES(3010, '对话使用', 3001, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:chat:use', '#', 100, 1, sysdate(), null, null, '使用 AI 对话');

-- 全员可使用 AI 对话（docs/21 §4）；可见性仍按账号隔离。
INSERT INTO sys_role_menu VALUES(20, 3000);
INSERT INTO sys_role_menu VALUES(20, 3001);
INSERT INTO sys_role_menu VALUES(20, 3010);
INSERT INTO sys_role_menu VALUES(21, 3000);
INSERT INTO sys_role_menu VALUES(21, 3001);
INSERT INTO sys_role_menu VALUES(21, 3010);
INSERT INTO sys_role_menu VALUES(22, 3000);
INSERT INTO sys_role_menu VALUES(22, 3001);
INSERT INTO sys_role_menu VALUES(22, 3010);
INSERT INTO sys_role_menu VALUES(23, 3000);
INSERT INTO sys_role_menu VALUES(23, 3001);
INSERT INTO sys_role_menu VALUES(23, 3010);
INSERT INTO sys_role_menu VALUES(24, 3000);
INSERT INTO sys_role_menu VALUES(24, 3001);
INSERT INTO sys_role_menu VALUES(24, 3010);
INSERT INTO sys_role_menu VALUES(25, 3000);
INSERT INTO sys_role_menu VALUES(25, 3001);
INSERT INTO sys_role_menu VALUES(25, 3010);
INSERT INTO sys_role_menu VALUES(26, 3000);
INSERT INTO sys_role_menu VALUES(26, 3001);
INSERT INTO sys_role_menu VALUES(26, 3010);
INSERT INTO sys_role_menu VALUES(27, 3000);
INSERT INTO sys_role_menu VALUES(27, 3001);
INSERT INTO sys_role_menu VALUES(27, 3010);
