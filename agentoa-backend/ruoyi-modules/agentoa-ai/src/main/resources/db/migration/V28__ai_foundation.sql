-- AgentOA module 10 (M1): AI model access layer - providers, models, usage log and quotas.
-- API contract: docs/21-module-plan-ai.md section 3. Business ids are assigned snowflake values.
-- API keys are AES-256-GCM ciphertexts (keyVersion:base64(nonce||tag||ct)); plaintext is never stored,
-- never echoed and never logged. secret_ref (env/configtree) takes precedence over the stored cipher.

-- ---------------------------------------------------------------- providers and models

CREATE TABLE oa_ai_provider (
  id              BIGINT        NOT NULL                   COMMENT '渠道ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  name            VARCHAR(128)  NOT NULL                   COMMENT '渠道名称',
  provider_type   VARCHAR(32)   NOT NULL DEFAULT 'custom'  COMMENT '厂商类型（ai_provider_type）',
  base_url        VARCHAR(255)  NOT NULL                   COMMENT 'OpenAI 兼容 Base URL（仅允许配置值出站）',
  api_key_cipher  VARCHAR(1024) DEFAULT NULL               COMMENT 'API Key 密文（AES-256-GCM）',
  api_key_hint    VARCHAR(32)   DEFAULT NULL               COMMENT 'API Key 脱敏提示（sk-***ab12）',
  secret_ref      VARCHAR(128)  DEFAULT NULL               COMMENT '密钥引用（环境变量/configtree 名，优先于密文）',
  priority        INT           NOT NULL DEFAULT 100       COMMENT '优先级（小值优先）',
  enabled         TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  create_dept     BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by       BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time     DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark          VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_provider_enabled (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型渠道（厂商 Key 渠道）';

CREATE TABLE oa_ai_model (
  id                 BIGINT        NOT NULL                   COMMENT '模型ID',
  tenant_id          VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  provider_id        BIGINT        NOT NULL                   COMMENT '所属渠道ID',
  model_key          VARCHAR(128)  NOT NULL                   COMMENT '上游模型标识',
  alias              VARCHAR(128)  DEFAULT NULL               COMMENT '显示别名',
  capability         VARCHAR(128)  NOT NULL DEFAULT '["chat"]' COMMENT '能力（JSON 数组：chat/vision/embedding/rerank）',
  context_window     INT           NOT NULL DEFAULT 8192      COMMENT '上下文窗口（token）',
  default_temperature DECIMAL(3,2) DEFAULT 0.70               COMMENT '默认温度',
  max_tokens         INT           DEFAULT 2048               COMMENT '默认最大输出 token',
  enabled            TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  is_default         TINYINT       NOT NULL DEFAULT 0         COMMENT '全局默认模型（1是 0否）',
  create_dept        BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_model_provider (provider_id),
  KEY idx_ai_model_key (model_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型（渠道下的具体模型）';

-- ---------------------------------------------------------------- usage log and quotas

CREATE TABLE oa_ai_usage_log (
  id                BIGINT        NOT NULL                   COMMENT '日志ID',
  tenant_id         VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id           BIGINT        NOT NULL                   COMMENT '调用账号ID',
  username          VARCHAR(64)   DEFAULT NULL               COMMENT '调用账号名',
  provider_id       BIGINT        DEFAULT NULL               COMMENT '渠道ID',
  model_key         VARCHAR(128)  DEFAULT NULL               COMMENT '模型标识',
  biz_type          VARCHAR(32)   NOT NULL DEFAULT 'chat'    COMMENT '业务类型（chat/rag/copilot/agent/probe）',
  conversation_id   BIGINT        DEFAULT NULL               COMMENT '会话ID',
  task_id           VARCHAR(64)   DEFAULT NULL               COMMENT '任务ID',
  prompt_tokens     INT           NOT NULL DEFAULT 0         COMMENT '输入 token',
  completion_tokens INT           NOT NULL DEFAULT 0         COMMENT '输出 token',
  total_tokens      INT           NOT NULL DEFAULT 0         COMMENT '合计 token',
  latency_ms        INT           DEFAULT NULL               COMMENT '耗时（毫秒）',
  status            VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT '状态（ai_usage_status）',
  error_code        VARCHAR(64)   DEFAULT NULL               COMMENT '错误码',
  error_msg         VARCHAR(500)  DEFAULT NULL               COMMENT '错误信息（脱敏）',
  create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_ai_usage_user_time (user_id, create_time),
  KEY idx_ai_usage_model_time (model_key, create_time),
  KEY idx_ai_usage_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用用量日志（异步落库，审计与限额依据）';

CREATE TABLE oa_ai_quota (
  id            BIGINT        NOT NULL                   COMMENT '配额ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  scope_type    VARCHAR(16)   NOT NULL                   COMMENT '主体类型（user/role）',
  scope_id      BIGINT        NOT NULL                   COMMENT '主体ID（账号ID/角色ID）',
  scope_name    VARCHAR(128)  DEFAULT NULL               COMMENT '主体名称（冗余展示）',
  period_type   VARCHAR(8)    NOT NULL                   COMMENT '周期（day/month）',
  token_limit   BIGINT        DEFAULT NULL               COMMENT 'token 限额（NULL不限）',
  request_limit INT           DEFAULT NULL               COMMENT '请求次数限额（NULL不限）',
  enabled       TINYINT       NOT NULL DEFAULT 1         COMMENT '启用状态（1启用 0停用）',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_quota (scope_type, scope_id, period_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量配额（主体+周期）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(140, '000000', 'AI 厂商类型', 'ai_provider_type', 100, 1, sysdate(), null, null, '模型渠道厂商');
INSERT INTO sys_dict_data VALUES(1400, '000000', 1,  'OpenAI',  'openai',   'ai_provider_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, 'OpenAI 兼容');
INSERT INTO sys_dict_data VALUES(1401, '000000', 2,  'DeepSeek','deepseek', 'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1402, '000000', 3,  '通义千问','qwen',     'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1403, '000000', 4,  'Kimi',   'moonshot', 'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1404, '000000', 5,  '智谱',   'zhipu',    'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1405, '000000', 6,  'Gemini', 'gemini',   'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1406, '000000', 7,  'Claude', 'anthropic','ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1407, '000000', 8,  'Ollama', 'ollama',   'ai_provider_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '本地部署');
INSERT INTO sys_dict_data VALUES(1408, '000000', 9,  '自定义', 'custom',   'ai_provider_type', '', 'info',    'N', 100, 1, sysdate(), null, null, '其他 OpenAI 兼容厂商');
INSERT INTO sys_dict_type VALUES(141, '000000', 'AI 模型能力', 'ai_model_capability', 100, 1, sysdate(), null, null, '模型多模态能力');
INSERT INTO sys_dict_data VALUES(1410, '000000', 1,  '对话',   'chat',     'ai_model_capability', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1411, '000000', 2,  '视觉',   'vision',   'ai_model_capability', '', 'success', 'N', 100, 1, sysdate(), null, null, '支持图片输入');
INSERT INTO sys_dict_data VALUES(1412, '000000', 3,  '向量化', 'embedding','ai_model_capability', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1413, '000000', 4,  '重排',   'rerank',   'ai_model_capability', '', 'info',    'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(142, '000000', 'AI 用量状态', 'ai_usage_status', 100, 1, sysdate(), null, null, '调用结果状态');
INSERT INTO sys_dict_data VALUES(1420, '000000', 1,  '成功',   'success',  'ai_usage_status', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1421, '000000', 2,  '失败',   'failed',   'ai_usage_status', '', 'danger',  'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1422, '000000', 3,  '已停止', 'stopped',  'ai_usage_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '用户主动停止生成');
INSERT INTO sys_dict_type VALUES(143, '000000', 'AI 配额主体', 'ai_quota_scope', 100, 1, sysdate(), null, null, '配额作用主体');
INSERT INTO sys_dict_data VALUES(1430, '000000', 1,  '用户',   'user',     'ai_quota_scope', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1431, '000000', 2,  '角色',   'role',     'ai_quota_scope', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(144, '000000', 'AI 配额周期', 'ai_quota_period', 100, 1, sysdate(), null, null, '配额统计周期');
INSERT INTO sys_dict_data VALUES(1440, '000000', 1,  '按日',   'day',      'ai_quota_period', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1441, '000000', 2,  '按月',   'month',    'ai_quota_period', '', 'success', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- menus and buttons (docs/21 §2.4)

INSERT INTO sys_menu VALUES(3020, 'AI 管理', 0, 30, 'aiadmin', null, '', 1, 0, 'M', '0', '0', '', 'robot', 100, 1, sysdate(), null, null, 'AI 能力管理目录');
INSERT INTO sys_menu VALUES(3021, '模型渠道', 3020, 1, 'provider', 'ai/provider/index', '', 1, 0, 'C', '0', '0', '', 'server', 100, 1, sysdate(), null, null, '厂商渠道与 Key 管理');
INSERT INTO sys_menu VALUES(3022, '模型管理', 3020, 2, 'model', 'ai/model/index', '', 1, 0, 'C', '0', '0', '', 'cpu', 100, 1, sysdate(), null, null, '模型与能力配置');
INSERT INTO sys_menu VALUES(3023, '提示词模板', 3020, 3, 'prompt', 'ai/prompt/index', '', 1, 0, 'C', '0', '0', '', 'edit', 100, 1, sysdate(), null, null, '提示词模板维护');
INSERT INTO sys_menu VALUES(3024, '用量与配额', 3020, 4, 'usage', 'ai/usage/index', '', 1, 0, 'C', '0', '0', '', 'chart', 100, 1, sysdate(), null, null, '用量统计与限额');
INSERT INTO sys_menu VALUES(3030, '渠道查询', 3021, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:provider:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3031, '渠道新增', 3021, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:provider:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3032, '渠道修改', 3021, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:provider:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3033, '渠道删除', 3021, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:provider:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3034, '测试连接', 3021, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:provider:test', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3040, '模型查询', 3022, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:model:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3041, '模型新增', 3022, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:model:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3042, '模型修改', 3022, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:model:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3043, '模型删除', 3022, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:model:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3050, '模板查询', 3023, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:prompt:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3051, '模板新增', 3023, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:prompt:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3052, '模板修改', 3023, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:prompt:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3053, '模板删除', 3023, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:prompt:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3060, '用量查询', 3024, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:usage:list', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3061, '用量导出', 3024, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:usage:export', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3062, '配额查询', 3024, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:quota:list', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3063, '配额编辑', 3024, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:quota:edit', '#', 100, 1, sysdate(), null, null, '');

-- AI 管理入口默认仅管理员（docs/21 §2.3）；超管按约定豁免权限串。
INSERT INTO sys_role_menu VALUES(26, 3020);
INSERT INTO sys_role_menu VALUES(26, 3021);
INSERT INTO sys_role_menu VALUES(26, 3022);
INSERT INTO sys_role_menu VALUES(26, 3023);
INSERT INTO sys_role_menu VALUES(26, 3024);
INSERT INTO sys_role_menu VALUES(26, 3030);
INSERT INTO sys_role_menu VALUES(26, 3031);
INSERT INTO sys_role_menu VALUES(26, 3032);
INSERT INTO sys_role_menu VALUES(26, 3033);
INSERT INTO sys_role_menu VALUES(26, 3034);
INSERT INTO sys_role_menu VALUES(26, 3040);
INSERT INTO sys_role_menu VALUES(26, 3041);
INSERT INTO sys_role_menu VALUES(26, 3042);
INSERT INTO sys_role_menu VALUES(26, 3043);
INSERT INTO sys_role_menu VALUES(26, 3050);
INSERT INTO sys_role_menu VALUES(26, 3051);
INSERT INTO sys_role_menu VALUES(26, 3052);
INSERT INTO sys_role_menu VALUES(26, 3053);
INSERT INTO sys_role_menu VALUES(26, 3060);
INSERT INTO sys_role_menu VALUES(26, 3061);
INSERT INTO sys_role_menu VALUES(26, 3062);
INSERT INTO sys_role_menu VALUES(26, 3063);
