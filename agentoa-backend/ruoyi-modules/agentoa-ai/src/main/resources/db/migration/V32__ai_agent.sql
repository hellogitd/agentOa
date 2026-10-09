-- AgentOA module 10 (M5): tools, MCP servers and agents.
-- API contract: docs/21-module-plan-ai.md section 7. 工具调用有权限边界：读类按调用者已有权限过滤，
-- 写类工具需 ai:tool:write + 人工确认后执行（docs/21 §7.1 AI-M5-05）。

-- ---------------------------------------------------------------- tools (function / mcp), agents, runs

CREATE TABLE oa_ai_tool (
  id          BIGINT        NOT NULL                   COMMENT '工具ID',
  tenant_id   VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  code        VARCHAR(64)   NOT NULL                   COMMENT '工具码（Agent 引用名）',
  name        VARCHAR(128)  NOT NULL                   COMMENT '工具名称',
  type        VARCHAR(16)   NOT NULL DEFAULT 'function' COMMENT '类型（ai_tool_type）function/mcp',
  description VARCHAR(500)  DEFAULT NULL               COMMENT '给模型看的用途说明',
  schema_json VARCHAR(4000) DEFAULT NULL               COMMENT '参数 JSON Schema',
  config_json VARCHAR(4000) DEFAULT NULL               COMMENT 'MCP 连接配置（url/stdio/鉴权头）',
  write_flag  TINYINT       NOT NULL DEFAULT 0         COMMENT '写类工具（1需 ai:tool:write + 人工确认）',
  enabled     TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  is_builtin  TINYINT       NOT NULL DEFAULT 0         COMMENT '是否内置（1内置 0自定义）',
  create_dept BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by   BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark      VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_tool_code (code),
  KEY idx_ai_tool_type (type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 工具（函数工具 / MCP server）';

CREATE TABLE oa_ai_agent (
  id             BIGINT        NOT NULL                   COMMENT 'AgentID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  code           VARCHAR(64)   NOT NULL                   COMMENT 'Agent码',
  name           VARCHAR(128)  NOT NULL                   COMMENT '名称',
  system_prompt  LONGTEXT      DEFAULT NULL               COMMENT '系统提示词',
  model_id       BIGINT        DEFAULT NULL               COMMENT '默认模型ID（空取全局默认）',
  tool_codes     VARCHAR(2000) DEFAULT NULL               COMMENT '可用工具码（JSON 数组）',
  max_steps      INT           NOT NULL DEFAULT 6         COMMENT '最大执行步数',
  timeout_sec    INT           NOT NULL DEFAULT 60        COMMENT '单次运行超时（秒）',
  enabled        TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  is_builtin     TINYINT       NOT NULL DEFAULT 0         COMMENT '是否内置（1内置 0自定义）',
  create_dept    BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_agent_code (code),
  KEY idx_ai_agent_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent（系统提示 + 工具集 + 步数/超时）';

CREATE TABLE oa_ai_agent_run (
  id            BIGINT        NOT NULL                   COMMENT '运行ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  agent_id      BIGINT        NOT NULL                   COMMENT 'AgentID',
  user_id       BIGINT        NOT NULL                   COMMENT '发起账号ID',
  conversation_id BIGINT      DEFAULT NULL               COMMENT '关联对话ID（Agent 模式复用会话）',
  input         TEXT          DEFAULT NULL               COMMENT '用户输入',
  trace_json    LONGTEXT      DEFAULT NULL               COMMENT '执行轨迹（JSON：逐步工具调用/参数/结果摘要）',
  output        LONGTEXT      DEFAULT NULL               COMMENT '最终输出（AI 生成内容仅供参考）',
  status        VARCHAR(16)   NOT NULL DEFAULT 'running' COMMENT '状态（running/done/failed/stopped）',
  error_msg     VARCHAR(500)  DEFAULT NULL               COMMENT '失败原因',
  total_tokens  INT           NOT NULL DEFAULT 0         COMMENT '合计 token',
  duration_ms   INT           NOT NULL DEFAULT 0         COMMENT '耗时（毫秒）',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_agent_run_agent (agent_id, id),
  KEY idx_ai_agent_run_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 运行记录（含多步执行轨迹）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(149, '000000', 'AI 工具类型', 'ai_tool_type', 100, 1, sysdate(), null, null, 'Agent 工具类型');
INSERT INTO sys_dict_data VALUES(1490, '000000', 1,  '函数工具', 'function', 'ai_tool_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '内置 Java 实现');
INSERT INTO sys_dict_data VALUES(1491, '000000', 2,  'MCP',     'mcp',      'ai_tool_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '外部 MCP server');

-- ---------------------------------------------------------------- built-in tools and agent

INSERT INTO oa_notice_template(id, template_code, name, title_tpl, content_tpl, msg_type, vars_json, status, remark)
VALUES(9001, 'ai_task_done', 'AI 任务完成提醒', 'AI 生成{result}：{scene}',
  '你的「{scene}」生成任务{result}（任务号 {taskId}）。AI 生成内容仅供参考，请人工核对后使用。', 'NOTICE',
  '["scene","taskId","result"]', 1, '内置模板：业务助手任务完成（docs/21 AI-M4-06）');

INSERT INTO oa_ai_tool VALUES(7001, '000000', 'knowledge_search', '知识库检索', 'function', '按关键词在已授权知识库中检索片段，返回标题与摘录', '{"type":"object","properties":{"query":{"type":"string","description":"检索关键词"}},"required":["query"]}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具（复用 M3）');
INSERT INTO oa_ai_tool VALUES(7002, '000000', 'todo_query', '待办查询', 'function', '查询当前用户的待办任务列表', '{"type":"object","properties":{"keyword":{"type":"string","description":"标题关键词"}}}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具');
INSERT INTO oa_ai_tool VALUES(7003, '000000', 'calendar_query', '日程查询', 'function', '按日期范围查询当前用户可见的日程', '{"type":"object","properties":{"start":{"type":"string"},"end":{"type":"string"}}}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具');
INSERT INTO oa_ai_tool VALUES(7004, '000000', 'calendar_create', '创建日程', 'function', '为当前用户创建日程（写类，需人工确认）', '{"type":"object","properties":{"title":{"type":"string"},"startTime":{"type":"string"},"endTime":{"type":"string"},"location":{"type":"string"}},"required":["title","startTime"]}', null, 1, 1, 1, 100, 1, sysdate(), null, null, '内置工具（写类）');
INSERT INTO oa_ai_tool VALUES(7005, '000000', 'notice_draft', '通知草稿', 'function', '生成通知草稿文本（不发布）', '{"type":"object","properties":{"title":{"type":"string"},"points":{"type":"string"}},"required":["title"]}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具');
INSERT INTO oa_ai_tool VALUES(7006, '000000', 'user_directory', '通讯录查询', 'function', '按姓名关键词查询同事账号信息', '{"type":"object","properties":{"keyword":{"type":"string"}},"required":["keyword"]}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具');
INSERT INTO oa_ai_tool VALUES(7007, '000000', 'leave_balance', '请假余额查询', 'function', '查询当前用户指定年份的各类假期剩余额度', '{"type":"object","properties":{"year":{"type":"integer","description":"年份，如 2026"}}}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具（仅本人）');
INSERT INTO oa_ai_tool VALUES(7008, '000000', 'reimburse_progress', '报销进度查询', 'function', '查询当前用户本人报销单的审批进度', '{"type":"object","properties":{"keyword":{"type":"string","description":"标题关键词"}}}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具（仅本人）');
INSERT INTO oa_ai_tool VALUES(7009, '000000', 'notice_search', '公告搜索', 'function', '按关键词检索已发布的公司公告，返回标题与摘要', '{"type":"object","properties":{"keyword":{"type":"string"}},"required":["keyword"]}', null, 0, 1, 1, 100, 1, sysdate(), null, null, '内置工具');

INSERT INTO oa_ai_agent VALUES(8001, '000000', 'office_assistant', '办公助手', '你是企业办公助手，可调用工具查询待办、日程、知识库、通讯录、假期余额、报销进度与公告。回答务必基于工具返回的事实，缺失信息要说明。AI 生成内容仅供参考，请人工核对后使用。', null, '["knowledge_search","todo_query","calendar_query","calendar_create","notice_draft","notice_search","user_directory","leave_balance","reimburse_progress"]', 6, 60, 1, 1, 100, 1, sysdate(), null, null, '内置 Agent');

-- ---------------------------------------------------------------- menus and buttons (docs/21 §2.4)

INSERT INTO sys_menu VALUES(3026, '工具与 MCP', 3020, 6, 'tool', 'ai/tool/index', '', 1, 0, 'C', '0', '0', '', 'connection', 100, 1, sysdate(), null, null, '内置工具与 MCP server 连接管理');
INSERT INTO sys_menu VALUES(3069, '工具查询', 3026, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3070, '工具新增', 3026, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3071, '工具修改', 3026, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3072, '工具删除', 3026, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3073, '工具测试', 3026, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:test', '#', 100, 1, sysdate(), null, null, '连通性/工具列表测试');
INSERT INTO sys_menu VALUES(3074, '写类工具执行', 3026, 6, '', '', '', 1, 0, 'F', '0', '0', 'ai:tool:write', '#', 100, 1, sysdate(), null, null, '需人工确认后执行');

INSERT INTO sys_menu VALUES(3027, 'Agent 管理', 3020, 7, 'agent', 'ai/agent/index', '', 1, 0, 'C', '0', '0', '', 'cpu', 100, 1, sysdate(), null, null, 'Agent 配置与运行记录');
INSERT INTO sys_menu VALUES(3075, 'Agent 查询', 3027, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:agent:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3076, 'Agent 新增', 3027, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:agent:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3077, 'Agent 修改', 3027, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:agent:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3078, 'Agent 删除', 3027, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:agent:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3079, 'Agent 运行', 3027, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:agent:run', '#', 100, 1, sysdate(), null, null, '运行与轨迹回放');

-- 工具与 Agent 管理默认仅管理员（docs/21 §2.3）。
INSERT INTO sys_role_menu VALUES(26, 3026);
INSERT INTO sys_role_menu VALUES(26, 3069);
INSERT INTO sys_role_menu VALUES(26, 3070);
INSERT INTO sys_role_menu VALUES(26, 3071);
INSERT INTO sys_role_menu VALUES(26, 3072);
INSERT INTO sys_role_menu VALUES(26, 3073);
INSERT INTO sys_role_menu VALUES(26, 3074);
INSERT INTO sys_role_menu VALUES(26, 3027);
INSERT INTO sys_role_menu VALUES(26, 3075);
INSERT INTO sys_role_menu VALUES(26, 3076);
INSERT INTO sys_role_menu VALUES(26, 3077);
INSERT INTO sys_role_menu VALUES(26, 3078);
INSERT INTO sys_role_menu VALUES(26, 3079);
