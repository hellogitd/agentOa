-- H2 (MODE=MySQL) schema for agentoa-notice tests. Mirrors V8 + referenced V2 tables.

CREATE TABLE sys_user (
  user_id     BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  dept_id     BIGINT       DEFAULT NULL,
  user_name   VARCHAR(30)  NOT NULL,
  nick_name   VARCHAR(30)  NOT NULL,
  user_type   VARCHAR(10)  DEFAULT 'sys_user',
  status      CHAR(1)      DEFAULT '0',
  del_flag    CHAR(1)      DEFAULT '0',
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
);

CREATE TABLE sys_dept (
  dept_id     BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  parent_id   BIGINT       DEFAULT 0,
  dept_name   VARCHAR(30)  DEFAULT '',
  leader      BIGINT       DEFAULT NULL,
  status      CHAR(1)      DEFAULT '0',
  del_flag    CHAR(1)      DEFAULT '0',
  PRIMARY KEY (dept_id)
);

CREATE TABLE sys_role (
  role_id    BIGINT       NOT NULL,
  tenant_id  VARCHAR(20)  DEFAULT '000000',
  role_name  VARCHAR(30)  NOT NULL,
  role_key   VARCHAR(100) NOT NULL,
  status     CHAR(1)      DEFAULT '0',
  del_flag   CHAR(1)      DEFAULT '0',
  PRIMARY KEY (role_id)
);

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_outbox (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  event_id          VARCHAR(64)  NOT NULL,
  receiver_id       BIGINT       DEFAULT NULL,
  event_type        VARCHAR(32)  NOT NULL,
  payload           CLOB         NOT NULL,
  status            VARCHAR(16)  DEFAULT 'PENDING',
  retry_count       INT          DEFAULT 0,
  next_attempt_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  last_error        VARCHAR(500) DEFAULT NULL,
  redeliver_count   INT          DEFAULT 0,
  redelivered_by    BIGINT       DEFAULT NULL,
  redelivered_time  TIMESTAMP    DEFAULT NULL,
  create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  processed_time    TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_outbox_event ON sys_outbox (event_id);

CREATE TABLE oa_idempotency (
  id            BIGINT       NOT NULL,
  idem_key      VARCHAR(64)  NOT NULL,
  user_id       BIGINT       NOT NULL,
  request_path  VARCHAR(255) NOT NULL,
  body_digest   CHAR(64)     NOT NULL,
  result_ref    VARCHAR(64)  DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_idem_user_key ON oa_idempotency (user_id, idem_key);

CREATE TABLE oa_announcement (
  id              BIGINT        NOT NULL,
  tenant_id       VARCHAR(20)   DEFAULT '000000',
  title           VARCHAR(255)  NOT NULL,
  content         CLOB          NOT NULL,
  notice_type     VARCHAR(32)   DEFAULT NULL,
  publisher_id    BIGINT        NOT NULL,
  publish_time    TIMESTAMP     DEFAULT NULL,
  effective_start TIMESTAMP     DEFAULT NULL,
  effective_end   TIMESTAMP     DEFAULT NULL,
  scope_type      TINYINT       DEFAULT 1,
  scope_values    VARCHAR(2000) DEFAULT NULL,
  is_top          TINYINT       DEFAULT 0,
  is_popup        TINYINT       DEFAULT 0,
  status          TINYINT       DEFAULT 1,
  read_count      INT           DEFAULT 0,
  attachments     CLOB          DEFAULT NULL,
  create_dept     BIGINT        DEFAULT NULL,
  create_by       BIGINT        DEFAULT NULL,
  create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT        DEFAULT NULL,
  update_time     TIMESTAMP     DEFAULT NULL,
  remark          VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_announcement_audience (
  notice_id   BIGINT    NOT NULL,
  user_id     BIGINT    NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notice_id, user_id)
);

CREATE TABLE oa_announcement_read (
  id        BIGINT    NOT NULL,
  notice_id BIGINT    NOT NULL,
  user_id   BIGINT    NOT NULL,
  read_time TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_announcement_read ON oa_announcement_read (notice_id, user_id);

CREATE TABLE oa_notification_preference (
  user_id     BIGINT      NOT NULL,
  msg_type    VARCHAR(32) NOT NULL,
  enabled     TINYINT     DEFAULT 1,
  update_time TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (user_id, msg_type)
);

CREATE TABLE oa_notice_template (
  id            BIGINT        NOT NULL,
  tenant_id     VARCHAR(20)   DEFAULT '000000',
  template_code VARCHAR(64)   NOT NULL,
  name          VARCHAR(128)  NOT NULL,
  title_tpl     VARCHAR(255)  NOT NULL,
  content_tpl   CLOB          NOT NULL,
  msg_type      VARCHAR(32)   DEFAULT 'NOTICE',
  vars_json     VARCHAR(2000) DEFAULT NULL,
  status        TINYINT       DEFAULT 1,
  create_by     BIGINT        DEFAULT NULL,
  create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT        DEFAULT NULL,
  update_time   TIMESTAMP     DEFAULT NULL,
  remark        VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_notice_template_code ON oa_notice_template (template_code);

CREATE TABLE oa_scheduled_push (
  id              BIGINT        NOT NULL,
  tenant_id       VARCHAR(20)   DEFAULT '000000',
  name            VARCHAR(128)  NOT NULL,
  push_type       TINYINT       NOT NULL,
  announcement_id BIGINT        DEFAULT NULL,
  template_id     BIGINT        DEFAULT NULL,
  scope_type      TINYINT       DEFAULT 1,
  scope_values    VARCHAR(2000) DEFAULT NULL,
  vars_json       VARCHAR(2000) DEFAULT NULL,
  schedule_type   TINYINT       DEFAULT 1,
  run_at          TIMESTAMP     DEFAULT NULL,
  cron_expr       VARCHAR(64)   DEFAULT NULL,
  next_run_time   TIMESTAMP     DEFAULT NULL,
  last_run_time   TIMESTAMP     DEFAULT NULL,
  run_count       INT           DEFAULT 0,
  last_error      VARCHAR(500)  DEFAULT NULL,
  status          TINYINT       DEFAULT 1,
  create_by       BIGINT        DEFAULT NULL,
  create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT        DEFAULT NULL,
  update_time     TIMESTAMP     DEFAULT NULL,
  remark          VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_scheduled_push_due ON oa_scheduled_push (status, next_run_time);

CREATE TABLE oa_scheduled_push_run (
  id             BIGINT       NOT NULL,
  push_id        BIGINT       NOT NULL,
  event_key      VARCHAR(64)  NOT NULL,
  slot_time      TIMESTAMP    NOT NULL,
  status         TINYINT      DEFAULT 1,
  receiver_count INT          DEFAULT 0,
  error          VARCHAR(500) DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_push_run_event ON oa_scheduled_push_run (event_key);
CREATE INDEX idx_push_run_push ON oa_scheduled_push_run (push_id, slot_time);
