-- H2 (MODE=MySQL) schema for agentoa-finance tests. Mirrors V7 + referenced V2/V5 tables.

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

CREATE TABLE sys_file (
  id            BIGINT       NOT NULL,
  bucket        VARCHAR(64)  NOT NULL,
  object_key    VARCHAR(512) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type  VARCHAR(128) NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  sha256        CHAR(64)     NOT NULL,
  owner_user_id BIGINT       NOT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
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

CREATE TABLE oa_reimburse_request (
  id               BIGINT        NOT NULL,
  tenant_id        VARCHAR(20)   DEFAULT '000000',
  reimburse_no     VARCHAR(32)   NOT NULL,
  user_id          BIGINT        NOT NULL,
  employee_id      BIGINT        DEFAULT NULL,
  reimburse_type   VARCHAR(32)   DEFAULT 'expense',
  total_amount     DECIMAL(12,2) DEFAULT 0.00,
  currency         VARCHAR(8)    DEFAULT 'CNY',
  pay_method       VARCHAR(32)   DEFAULT NULL,
  details_json     CLOB          DEFAULT NULL,
  reason           VARCHAR(500)  DEFAULT NULL,
  status           TINYINT       DEFAULT 1,
  flow_instance_id BIGINT        DEFAULT NULL,
  submission_no    INT           DEFAULT 0,
  paid_amount      DECIMAL(12,2) DEFAULT 0.00,
  lock_version     INT           DEFAULT 0,
  create_dept      BIGINT        DEFAULT NULL,
  create_by        BIGINT        DEFAULT NULL,
  create_time      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT        DEFAULT NULL,
  update_time      TIMESTAMP     DEFAULT NULL,
  remark           VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_reimburse_no ON oa_reimburse_request (reimburse_no);

CREATE TABLE oa_expense_type (
  id             BIGINT       NOT NULL,
  parent_id      BIGINT       DEFAULT 0,
  name           VARCHAR(64)  NOT NULL,
  code           VARCHAR(32)  NOT NULL,
  sort           INT          DEFAULT 0,
  budget_control TINYINT      DEFAULT 0,
  status         CHAR(1)      DEFAULT '0',
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_expense_type_code ON oa_expense_type (code);

CREATE TABLE oa_invoice (
  id                    BIGINT        NOT NULL,
  invoice_type          VARCHAR(32)   NOT NULL,
  invoice_code          VARCHAR(32)   DEFAULT '',
  invoice_no            VARCHAR(64)   NOT NULL,
  invoice_date          DATE          DEFAULT NULL,
  amount                DECIMAL(12,2) NOT NULL,
  fingerprint           CHAR(64)      NOT NULL,
  file_id               BIGINT        DEFAULT NULL,
  owner_user_id         BIGINT        NOT NULL,
  occupied_reimburse_id BIGINT        DEFAULT NULL,
  paid_reimburse_id     BIGINT        DEFAULT NULL,
  lock_version          INT           DEFAULT 0,
  create_time           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_time           TIMESTAMP     DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_invoice_identity ON oa_invoice (invoice_type, invoice_code, invoice_no);

CREATE TABLE oa_invoice_reservation (
  id           BIGINT       NOT NULL,
  invoice_id   BIGINT       NOT NULL,
  reimburse_id BIGINT       NOT NULL,
  action       VARCHAR(16)  NOT NULL,
  event_key    VARCHAR(128) NOT NULL,
  operator_id  BIGINT       NOT NULL,
  create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_reservation_event ON oa_invoice_reservation (event_key);

CREATE TABLE oa_expense_item (
  id              BIGINT        NOT NULL,
  reimburse_id    BIGINT        NOT NULL,
  submission_no   INT           DEFAULT 0,
  invoice_id      BIGINT        DEFAULT NULL,
  expense_type_id BIGINT        DEFAULT NULL,
  expense_type    VARCHAR(32)   DEFAULT NULL,
  occur_date      DATE          DEFAULT NULL,
  amount          DECIMAL(12,2) NOT NULL,
  description     VARCHAR(255)  DEFAULT NULL,
  sort            INT           DEFAULT 0,
  create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX idx_item_reimburse_invoice ON oa_expense_item (reimburse_id, invoice_id);

CREATE TABLE oa_payment_record (
  id             BIGINT        NOT NULL,
  payment_no     VARCHAR(32)   NOT NULL,
  reimburse_id   BIGINT        NOT NULL,
  amount         DECIMAL(12,2) NOT NULL,
  pay_date       DATE          DEFAULT NULL,
  payment_method VARCHAR(32)   DEFAULT NULL,
  voucher_no     VARCHAR(64)   DEFAULT NULL,
  pay_status     TINYINT       DEFAULT 2,
  operator_id    BIGINT        NOT NULL,
  create_dept    BIGINT        DEFAULT NULL,
  create_by      BIGINT        DEFAULT NULL,
  create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT        DEFAULT NULL,
  update_time    TIMESTAMP     DEFAULT NULL,
  remark         VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_payment_no ON oa_payment_record (payment_no);
CREATE INDEX idx_payment_reimburse ON oa_payment_record (reimburse_id);

CREATE TABLE oa_finance_event (
  id           BIGINT        NOT NULL,
  event_key    VARCHAR(128)  NOT NULL,
  biz_type     VARCHAR(32)   NOT NULL,
  biz_id       BIGINT        NOT NULL,
  action       VARCHAR(32)   NOT NULL,
  amount       DECIMAL(12,2) DEFAULT NULL,
  operator_id  BIGINT        NOT NULL,
  remark       VARCHAR(500)  DEFAULT NULL,
  create_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_finance_event ON oa_finance_event (event_key);

ALTER TABLE oa_reimburse_request ADD COLUMN budget_id BIGINT DEFAULT NULL;

CREATE TABLE oa_budget (
  id                  BIGINT        NOT NULL,
  tenant_id           VARCHAR(20)   DEFAULT '000000',
  budget_code         VARCHAR(32)   NOT NULL,
  budget_name         VARCHAR(64)   DEFAULT NULL,
  budget_type         TINYINT       NOT NULL,
  owner_id            BIGINT        NOT NULL,
  year                INT           NOT NULL,
  quarter             INT           DEFAULT NULL,
  total_amount        DECIMAL(14,2) NOT NULL,
  used_amount         DECIMAL(14,2) DEFAULT 0,
  frozen_amount       DECIMAL(14,2) DEFAULT 0,
  warn_threshold      INT           DEFAULT 80,
  status              TINYINT       DEFAULT 1,
  lock_version        INT           DEFAULT 0,
  create_dept         BIGINT        DEFAULT NULL,
  create_by           BIGINT        DEFAULT NULL,
  create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by           BIGINT        DEFAULT NULL,
  update_time         TIMESTAMP     DEFAULT NULL,
  remark              VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_budget_code ON oa_budget (budget_code);

CREATE TABLE oa_budget_ledger (
  id             BIGINT        NOT NULL,
  tenant_id      VARCHAR(20)   DEFAULT '000000',
  budget_id      BIGINT        NOT NULL,
  event_key      VARCHAR(128)  NOT NULL,
  action         VARCHAR(16)   NOT NULL,
  amount         DECIMAL(14,2) NOT NULL,
  biz_type       VARCHAR(32)   DEFAULT NULL,
  biz_id         BIGINT        DEFAULT NULL,
  operator_id    BIGINT        DEFAULT NULL,
  create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_budget_event ON oa_budget_ledger (budget_id, event_key);

ALTER TABLE oa_budget_ledger ADD COLUMN create_dept BIGINT DEFAULT NULL;
ALTER TABLE oa_budget_ledger ADD COLUMN create_by BIGINT DEFAULT NULL;
ALTER TABLE oa_budget_ledger ADD COLUMN update_by BIGINT DEFAULT NULL;
ALTER TABLE oa_budget_ledger ADD COLUMN update_time TIMESTAMP DEFAULT NULL;

CREATE TABLE oa_invoice_allocation (
  id              BIGINT        NOT NULL,
  invoice_id      BIGINT        NOT NULL,
  reimburse_id    BIGINT        NOT NULL,
  expense_item_id BIGINT        DEFAULT NULL,
  amount          DECIMAL(12,2) NOT NULL,
  event_key       VARCHAR(128)  NOT NULL,
  operator_id     BIGINT        NOT NULL,
  create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_allocation_event ON oa_invoice_allocation (event_key);