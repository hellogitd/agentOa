CREATE TABLE oa_ai_provider (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  name            VARCHAR(128) NOT NULL,
  provider_type   VARCHAR(32)  DEFAULT 'custom',
  base_url        VARCHAR(255) NOT NULL,
  api_key_cipher  VARCHAR(1024) DEFAULT NULL,
  api_key_hint    VARCHAR(32)  DEFAULT NULL,
  secret_ref      VARCHAR(128) DEFAULT NULL,
  priority        INT          DEFAULT 100,
  enabled         TINYINT      DEFAULT 1,
  create_dept     BIGINT       DEFAULT NULL,
  create_by       BIGINT       DEFAULT NULL,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT       DEFAULT NULL,
  update_time     TIMESTAMP    DEFAULT NULL,
  remark          VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_model (
  id                  BIGINT       NOT NULL,
  tenant_id           VARCHAR(20)  DEFAULT '000000',
  provider_id         BIGINT       NOT NULL,
  model_key           VARCHAR(128) NOT NULL,
  alias               VARCHAR(128) DEFAULT NULL,
  capability          VARCHAR(128) DEFAULT '["chat"]',
  context_window      INT          DEFAULT 8192,
  default_temperature DECIMAL(3,2) DEFAULT 0.70,
  max_tokens          INT          DEFAULT 2048,
  enabled             TINYINT      DEFAULT 1,
  is_default          TINYINT      DEFAULT 0,
  create_dept         BIGINT       DEFAULT NULL,
  create_by           BIGINT       DEFAULT NULL,
  create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by           BIGINT       DEFAULT NULL,
  update_time         TIMESTAMP    DEFAULT NULL,
  remark              VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_usage_log (
  id                BIGINT        NOT NULL,
  tenant_id         VARCHAR(20)   DEFAULT '000000',
  user_id           BIGINT        NOT NULL,
  username          VARCHAR(64)   DEFAULT NULL,
  provider_id       BIGINT        DEFAULT NULL,
  model_key         VARCHAR(128)  DEFAULT NULL,
  biz_type          VARCHAR(32)   DEFAULT 'chat',
  conversation_id   BIGINT        DEFAULT NULL,
  task_id           VARCHAR(64)   DEFAULT NULL,
  prompt_tokens     INT           DEFAULT 0,
  completion_tokens INT           DEFAULT 0,
  total_tokens      INT           DEFAULT 0,
  latency_ms        INT           DEFAULT NULL,
  status            VARCHAR(16)   DEFAULT 'success',
  error_code        VARCHAR(64)   DEFAULT NULL,
  error_msg         VARCHAR(500)  DEFAULT NULL,
  create_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_quota (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  scope_type    VARCHAR(16)  NOT NULL,
  scope_id      BIGINT       NOT NULL,
  scope_name    VARCHAR(128) DEFAULT NULL,
  period_type   VARCHAR(8)   NOT NULL,
  token_limit   BIGINT       DEFAULT NULL,
  request_limit INT          DEFAULT NULL,
  enabled       TINYINT      DEFAULT 1,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_quota ON oa_ai_quota (scope_type, scope_id, period_type);

CREATE TABLE oa_ai_conversation (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  user_id            BIGINT       NOT NULL,
  title              VARCHAR(255) DEFAULT NULL,
  model_id           BIGINT       DEFAULT NULL,
  prompt_template_id BIGINT       DEFAULT NULL,
  status             VARCHAR(16)  DEFAULT 'active',
  scene              VARCHAR(16)  DEFAULT 'chat',
  last_message_time  TIMESTAMP    DEFAULT NULL,
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_message (
  id                BIGINT        NOT NULL,
  tenant_id         VARCHAR(20)   DEFAULT '000000',
  conversation_id   BIGINT        NOT NULL,
  role              VARCHAR(16)   NOT NULL,
  content           TEXT          DEFAULT NULL,
  attachments       VARCHAR(2000) DEFAULT NULL,
  citations         TEXT          DEFAULT NULL,
  model_id          BIGINT        DEFAULT NULL,
  prompt_tokens     INT           DEFAULT 0,
  completion_tokens INT           DEFAULT 0,
  status            VARCHAR(16)   DEFAULT 'done',
  error_code        VARCHAR(64)   DEFAULT NULL,
  create_dept       BIGINT        DEFAULT NULL,
  create_by         BIGINT        DEFAULT NULL,
  create_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by         BIGINT        DEFAULT NULL,
  update_time       TIMESTAMP     DEFAULT NULL,
  remark            VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_prompt_template (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  code        VARCHAR(64)  NOT NULL,
  name        VARCHAR(128) NOT NULL,
  category    VARCHAR(64)  DEFAULT NULL,
  content     TEXT         NOT NULL,
  enabled     TINYINT      DEFAULT 1,
  is_builtin  TINYINT      DEFAULT 0,
  create_dept BIGINT       DEFAULT NULL,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  remark      VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_prompt_code ON oa_ai_prompt_template (code);

CREATE TABLE oa_ai_kb (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  name               VARCHAR(128) NOT NULL,
  description        VARCHAR(500) DEFAULT NULL,
  visibility         VARCHAR(16)  DEFAULT 'private',
  member_scope       VARCHAR(2000) DEFAULT NULL,
  embedding_model_id BIGINT       DEFAULT NULL,
  status             VARCHAR(16)  DEFAULT 'active',
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_kb_source (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  kb_id         BIGINT       NOT NULL,
  source_type   VARCHAR(16)  NOT NULL,
  doc_id        BIGINT       DEFAULT NULL,
  file_id       BIGINT       DEFAULT NULL,
  title         VARCHAR(255) DEFAULT NULL,
  chunk_count   INT          DEFAULT 0,
  index_status  VARCHAR(16)  DEFAULT 'pending',
  error_msg     VARCHAR(500) DEFAULT NULL,
  indexed_at    TIMESTAMP    DEFAULT NULL,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_kb_chunk (
  id           BIGINT       NOT NULL,
  tenant_id    VARCHAR(20)  DEFAULT '000000',
  kb_id        BIGINT       NOT NULL,
  source_id    BIGINT       NOT NULL,
  seq          INT          DEFAULT 0,
  heading      VARCHAR(255) DEFAULT NULL,
  content      TEXT         NOT NULL,
  token_count  INT          DEFAULT 0,
  embedding    BLOB         DEFAULT NULL,
  store_type   VARCHAR(16)  DEFAULT 'blob',
  create_dept  BIGINT       DEFAULT NULL,
  create_by    BIGINT       DEFAULT NULL,
  create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by    BIGINT       DEFAULT NULL,
  update_time  TIMESTAMP    DEFAULT NULL,
  remark       VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_user (
  user_id   BIGINT       NOT NULL,
  user_name VARCHAR(64)  DEFAULT NULL,
  nick_name VARCHAR(64)  DEFAULT NULL,
  del_flag  VARCHAR(1)   DEFAULT '0',
  PRIMARY KEY (user_id)
);

CREATE TABLE sys_file (
  id            BIGINT       NOT NULL,
  bucket        VARCHAR(64)  DEFAULT NULL,
  object_key    VARCHAR(255) DEFAULT NULL,
  original_name VARCHAR(255) DEFAULT NULL,
  content_type  VARCHAR(128) DEFAULT NULL,
  size_bytes    BIGINT       DEFAULT NULL,
  sha256        VARCHAR(64)  DEFAULT NULL,
  owner_user_id BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_copilot_config (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  scene_code         VARCHAR(64)  NOT NULL,
  scene_name         VARCHAR(128) NOT NULL,
  enabled            TINYINT      DEFAULT 1,
  model_id           BIGINT       DEFAULT NULL,
  prompt_template_id BIGINT       DEFAULT NULL,
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_copilot_scene ON oa_ai_copilot_config (scene_code);

CREATE TABLE oa_ai_copilot_task (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  scene_code    VARCHAR(64)  NOT NULL,
  biz_type      VARCHAR(32)  DEFAULT NULL,
  biz_id        BIGINT       DEFAULT NULL,
  user_id       BIGINT       NOT NULL,
  input_ref     TEXT         DEFAULT NULL,
  output        TEXT         DEFAULT NULL,
  status        VARCHAR(16)  DEFAULT 'pending',
  error_msg     VARCHAR(500) DEFAULT NULL,
  prompt_tokens INT          DEFAULT 0,
  total_tokens  INT          DEFAULT 0,
  finish_time   TIMESTAMP    DEFAULT NULL,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_ai_tool (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  code        VARCHAR(64)  NOT NULL,
  name        VARCHAR(128) NOT NULL,
  type        VARCHAR(16)  DEFAULT 'function',
  description VARCHAR(500) DEFAULT NULL,
  schema_json VARCHAR(4000) DEFAULT NULL,
  config_json VARCHAR(4000) DEFAULT NULL,
  write_flag  TINYINT      DEFAULT 0,
  enabled     TINYINT      DEFAULT 1,
  is_builtin  TINYINT      DEFAULT 0,
  create_dept BIGINT       DEFAULT NULL,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  remark      VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_tool_code ON oa_ai_tool (code);

CREATE TABLE oa_ai_agent (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  code          VARCHAR(64)  NOT NULL,
  name          VARCHAR(128) NOT NULL,
  system_prompt TEXT         DEFAULT NULL,
  model_id      BIGINT       DEFAULT NULL,
  tool_codes    VARCHAR(2000) DEFAULT NULL,
  max_steps     INT          DEFAULT 6,
  timeout_sec   INT          DEFAULT 60,
  enabled       TINYINT      DEFAULT 1,
  is_builtin    TINYINT      DEFAULT 0,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_agent_code ON oa_ai_agent (code);

CREATE TABLE oa_ai_agent_run (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  agent_id        BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  conversation_id BIGINT       DEFAULT NULL,
  input           TEXT         DEFAULT NULL,
  trace_json      TEXT         DEFAULT NULL,
  output          TEXT         DEFAULT NULL,
  status          VARCHAR(16)  DEFAULT 'running',
  error_msg       VARCHAR(500) DEFAULT NULL,
  total_tokens    INT          DEFAULT 0,
  duration_ms     INT          DEFAULT 0,
  create_dept     BIGINT       DEFAULT NULL,
  create_by       BIGINT       DEFAULT NULL,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT       DEFAULT NULL,
  update_time     TIMESTAMP    DEFAULT NULL,
  remark          VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
