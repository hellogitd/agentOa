-- H2 (MODE=MySQL) schema for agentoa-reporting tests. Mirrors V11 + referenced V2/V4-V10/Flowable tables.

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

CREATE TABLE oa_employee (
  id                  BIGINT       NOT NULL,
  tenant_id           VARCHAR(20)  DEFAULT '000000',
  user_id             BIGINT       DEFAULT NULL,
  employee_no         VARCHAR(32)  NOT NULL,
  name                VARCHAR(64)  NOT NULL,
  gender              CHAR(1)      DEFAULT '2',
  birth_date          DATE         DEFAULT NULL,
  id_card_no          VARCHAR(18)  DEFAULT NULL,
  phone               VARCHAR(11)  DEFAULT NULL,
  email               VARCHAR(50)  DEFAULT NULL,
  dept_id             BIGINT       NOT NULL,
  post_id             BIGINT       DEFAULT NULL,
  position_level      VARCHAR(32)  DEFAULT NULL,
  direct_leader_id    BIGINT       DEFAULT NULL,
  status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  entry_date          DATE         DEFAULT NULL,
  probation_end_date  DATE         DEFAULT NULL,
  regular_date        DATE         DEFAULT NULL,
  leave_date          DATE         DEFAULT NULL,
  workflow_instance_id VARCHAR(64) DEFAULT NULL,
  create_dept         BIGINT       DEFAULT NULL,
  create_by           BIGINT       DEFAULT NULL,
  create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by           BIGINT       DEFAULT NULL,
  update_time         TIMESTAMP    DEFAULT NULL,
  remark              VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_attendance_day (
  id                BIGINT     NOT NULL,
  tenant_id         VARCHAR(20) DEFAULT '000000',
  user_id           BIGINT     NOT NULL,
  employee_id       BIGINT     DEFAULT NULL,
  dept_id           BIGINT     DEFAULT NULL,
  attendance_date   DATE       NOT NULL,
  group_id          BIGINT     DEFAULT NULL,
  shift_id          BIGINT     DEFAULT NULL,
  rule_version      INT        DEFAULT 1,
  first_punch_time  TIMESTAMP  DEFAULT NULL,
  last_punch_time   TIMESTAMP  DEFAULT NULL,
  work_status       TINYINT    DEFAULT 0,
  missing_punch     TINYINT    DEFAULT 0,
  scheduled_minutes INT        DEFAULT 0,
  worked_minutes    INT        DEFAULT 0,
  late_minutes      INT        DEFAULT 0,
  early_minutes     INT        DEFAULT 0,
  leave_minutes     INT        DEFAULT 0,
  overtime_minutes  INT        DEFAULT 0,
  is_abnormal       TINYINT    DEFAULT 0,
  abnormal_reason   VARCHAR(255) DEFAULT NULL,
  create_time       TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMP  DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_punch_record (
  id                    BIGINT    NOT NULL,
  tenant_id             VARCHAR(20) DEFAULT '000000',
  user_id               BIGINT    NOT NULL,
  employee_id           BIGINT    DEFAULT NULL,
  punch_date            DATE      NOT NULL,
  punch_time            TIMESTAMP NOT NULL,
  punch_type            TINYINT   NOT NULL,
  correction_request_id BIGINT    DEFAULT NULL,
  is_late               TINYINT   DEFAULT 0,
  late_minutes          INT       DEFAULT 0,
  is_early              TINYINT   DEFAULT 0,
  early_minutes         INT       DEFAULT 0,
  create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_leave_balance (
  id             BIGINT      NOT NULL,
  tenant_id      VARCHAR(20) DEFAULT '000000',
  user_id        BIGINT      NOT NULL,
  employee_id    BIGINT      DEFAULT NULL,
  year           INT         NOT NULL,
  leave_type     VARCHAR(32) NOT NULL,
  total_minutes  INT         DEFAULT 0,
  frozen_minutes INT         DEFAULT 0,
  used_minutes   INT         DEFAULT 0,
  lock_version   INT         DEFAULT 0,
  expire_date    DATE        DEFAULT NULL,
  create_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_leave_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  user_id          BIGINT       NOT NULL,
  employee_id      BIGINT       DEFAULT NULL,
  leave_type       VARCHAR(32)  NOT NULL,
  start_time       TIMESTAMP    NOT NULL,
  end_time         TIMESTAMP    NOT NULL,
  duration_minutes INT          DEFAULT 0,
  reason           VARCHAR(500) DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_reimburse_request (
  id               BIGINT        NOT NULL,
  tenant_id        VARCHAR(20)   DEFAULT '000000',
  status           TINYINT       DEFAULT 1,
  flow_instance_id BIGINT        DEFAULT NULL,
  submission_no    INT           DEFAULT 0,
  lock_version     INT           DEFAULT 0,
  paid_amount      DECIMAL(12,2) DEFAULT 0.00,
  budget_id        BIGINT        DEFAULT NULL,
  reimburse_no     VARCHAR(32)   NOT NULL,
  user_id          BIGINT        NOT NULL,
  employee_id      BIGINT        DEFAULT NULL,
  reimburse_type   VARCHAR(32)   DEFAULT 'expense',
  total_amount     DECIMAL(12,2) DEFAULT 0.00,
  currency         VARCHAR(8)    DEFAULT 'CNY',
  pay_method       VARCHAR(32)   DEFAULT NULL,
  reason           VARCHAR(500)  DEFAULT NULL,
  create_time      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP     DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_expense_item (
  id              BIGINT        NOT NULL,
  tenant_id       VARCHAR(20)   DEFAULT '000000',
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

CREATE TABLE oa_expense_type (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  parent_id       BIGINT       DEFAULT 0,
  name            VARCHAR(64)  NOT NULL,
  code            VARCHAR(32)  NOT NULL,
  sort            INT          DEFAULT 0,
  budget_control  TINYINT      DEFAULT 0,
  status          CHAR(1)      DEFAULT '0',
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_payment_record (
  id             BIGINT        NOT NULL,
  tenant_id      VARCHAR(20)   DEFAULT '000000',
  payment_no     VARCHAR(32)   NOT NULL,
  reimburse_id   BIGINT        NOT NULL,
  amount         DECIMAL(12,2) NOT NULL,
  pay_date       DATE          DEFAULT NULL,
  payment_method VARCHAR(32)   DEFAULT NULL,
  voucher_no     VARCHAR(64)   DEFAULT NULL,
  pay_status     TINYINT       DEFAULT 2,
  operator_id    BIGINT        NOT NULL,
  create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP     DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_flow_instance (
  id                   BIGINT        NOT NULL,
  tenant_id            VARCHAR(20)   DEFAULT '000000',
  definition_id        BIGINT        NOT NULL,
  definition_version_id BIGINT       NOT NULL,
  form_version_id      BIGINT        NOT NULL,
  business_type        VARCHAR(32)   NOT NULL,
  business_id          BIGINT        NOT NULL,
  business_key         VARCHAR(64)   DEFAULT NULL,
  submission_no        INT           DEFAULT 1,
  title                VARCHAR(255)  DEFAULT NULL,
  initiator_user_id    BIGINT        NOT NULL,
  initiator_name       VARCHAR(64)   DEFAULT NULL,
  initiator_dept_id    BIGINT        DEFAULT NULL,
  priority             TINYINT       DEFAULT 0,
  status               TINYINT       DEFAULT 1,
  flowable_proc_inst_id VARCHAR(64)  DEFAULT NULL,
  current_task_name    VARCHAR(64)   DEFAULT NULL,
  current_assignees    VARCHAR(255)  DEFAULT NULL,
  start_time           TIMESTAMP     DEFAULT NULL,
  end_time             TIMESTAMP     DEFAULT NULL,
  duration             BIGINT        DEFAULT NULL,
  lock_version         INT           DEFAULT 0,
  create_time          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP     DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_calendar_event (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  lock_version  INT          DEFAULT 0,
  title         VARCHAR(255) NOT NULL,
  event_type    TINYINT      DEFAULT 1,
  start_time    TIMESTAMP    NOT NULL,
  end_time      TIMESTAMP    NOT NULL,
  is_all_day    TINYINT      DEFAULT 0,
  organizer_id  BIGINT       NOT NULL,
  visibility    TINYINT      DEFAULT 2,
  remind_minutes INT         DEFAULT 15,
  status        TINYINT      DEFAULT 1,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  del_flag      TINYINT      DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE oa_calendar_attendee (
  event_id        BIGINT      NOT NULL,
  user_id         BIGINT      NOT NULL,
  response_status VARCHAR(16) DEFAULT 'PENDING',
  create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id, user_id)
);

CREATE TABLE oa_announcement (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  title           VARCHAR(255) NOT NULL,
  content         CLOB         DEFAULT NULL,
  publisher_id    BIGINT       NOT NULL,
  publish_time    TIMESTAMP    DEFAULT NULL,
  is_top          TINYINT      DEFAULT 0,
  status          TINYINT      DEFAULT 1,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_announcement_audience (
  notice_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notice_id, user_id)
);

CREATE TABLE oa_report_metric (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  metric_code     VARCHAR(64)  NOT NULL,
  metric_name     VARCHAR(64)  NOT NULL,
  category        VARCHAR(32)  NOT NULL,
  unit            VARCHAR(32)  DEFAULT NULL,
  description     VARCHAR(500) DEFAULT NULL,
  current_version INT          DEFAULT 1,
  status          TINYINT      DEFAULT 1,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_report_metric_version (
  id             BIGINT       NOT NULL,
  metric_code    VARCHAR(64)  NOT NULL,
  version        INT          NOT NULL,
  definition     CLOB         NOT NULL,
  status         TINYINT      DEFAULT 1,
  effective_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  created_by     BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_report_export (
  id              BIGINT        NOT NULL,
  tenant_id       VARCHAR(20)   DEFAULT '000000',
  export_no       VARCHAR(32)   NOT NULL,
  report_type     VARCHAR(32)   NOT NULL,
  metric_version  INT           DEFAULT 1,
  format          VARCHAR(8)    DEFAULT 'CSV',
  filters         VARCHAR(2000) DEFAULT NULL,
  status          VARCHAR(16)   DEFAULT 'PENDING',
  row_count       INT           DEFAULT NULL,
  file_id         BIGINT        DEFAULT NULL,
  error_message   VARCHAR(500)  DEFAULT NULL,
  idempotency_key VARCHAR(64)   DEFAULT NULL,
  requested_by    BIGINT        NOT NULL,
  create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  finish_time     TIMESTAMP     DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_report_export_no ON oa_report_export (export_no);
CREATE UNIQUE INDEX uk_report_export_idem ON oa_report_export (requested_by, idempotency_key);

CREATE TABLE ACT_RU_TASK (
  ID_          VARCHAR(64) NOT NULL,
  REV_         INT DEFAULT NULL,
  EXECUTION_ID_ VARCHAR(64) DEFAULT NULL,
  PROC_INST_ID_ VARCHAR(64) DEFAULT NULL,
  PROC_DEF_ID_ VARCHAR(64) DEFAULT NULL,
  TASK_DEF_ID_ VARCHAR(64) DEFAULT NULL,
  NAME_        VARCHAR(255) DEFAULT NULL,
  TASK_DEF_KEY_ VARCHAR(255) DEFAULT NULL,
  OWNER_       VARCHAR(255) DEFAULT NULL,
  ASSIGNEE_    VARCHAR(255) DEFAULT NULL,
  PRIORITY_    INT DEFAULT NULL,
  CREATE_TIME_ TIMESTAMP DEFAULT NULL,
  DUE_DATE_    TIMESTAMP DEFAULT NULL,
  CATEGORY_    VARCHAR(255) DEFAULT NULL,
  SUSPENSION_STATE_ INT DEFAULT NULL,
  TENANT_ID_   VARCHAR(255) DEFAULT '',
  PRIMARY KEY (ID_)
);

CREATE TABLE ACT_RU_IDENTITYLINK (
  ID_          VARCHAR(64) NOT NULL,
  REV_         INT DEFAULT NULL,
  GROUP_ID_    VARCHAR(255) DEFAULT NULL,
  TYPE_        VARCHAR(255) DEFAULT NULL,
  USER_ID_     VARCHAR(255) DEFAULT NULL,
  TASK_ID_     VARCHAR(64) DEFAULT NULL,
  PROC_INST_ID_ VARCHAR(64) DEFAULT NULL,
  PROC_DEF_ID_ VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (ID_)
);

CREATE TABLE ACT_HI_TASKINST (
  ID_          VARCHAR(64) NOT NULL,
  REV_         INT DEFAULT 1,
  PROC_DEF_ID_ VARCHAR(64) DEFAULT NULL,
  TASK_DEF_KEY_ VARCHAR(255) DEFAULT NULL,
  PROC_INST_ID_ VARCHAR(64) DEFAULT NULL,
  EXECUTION_ID_ VARCHAR(64) DEFAULT NULL,
  NAME_        VARCHAR(255) DEFAULT NULL,
  OWNER_       VARCHAR(255) DEFAULT NULL,
  ASSIGNEE_    VARCHAR(255) DEFAULT NULL,
  START_TIME_  TIMESTAMP NOT NULL,
  END_TIME_    TIMESTAMP DEFAULT NULL,
  DURATION_    BIGINT DEFAULT NULL,
  TENANT_ID_   VARCHAR(255) DEFAULT '',
  PRIMARY KEY (ID_)
);
