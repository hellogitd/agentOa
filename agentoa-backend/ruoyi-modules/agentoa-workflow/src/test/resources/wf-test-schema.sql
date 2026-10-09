CREATE TABLE sys_user (
  user_id     BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  dept_id     BIGINT       DEFAULT NULL,
  user_name   VARCHAR(30)  NOT NULL,
  nick_name   VARCHAR(30)  NOT NULL,
  user_type   VARCHAR(10)  DEFAULT 'sys_user',
  email       VARCHAR(50)  DEFAULT '',
  phonenumber VARCHAR(11)  DEFAULT '',
  sex         CHAR(1)      DEFAULT '0',
  avatar      BIGINT       DEFAULT NULL,
  password    VARCHAR(100) DEFAULT '',
  must_change_password TINYINT DEFAULT 1,
  status      CHAR(1)      DEFAULT '0',
  del_flag    CHAR(1)      DEFAULT '0',
  login_ip    VARCHAR(128) DEFAULT '',
  login_date  TIMESTAMP    DEFAULT NULL,
  remark      VARCHAR(500) DEFAULT NULL,
  create_dept BIGINT       DEFAULT NULL,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (user_id)
);

CREATE TABLE sys_dept (
  dept_id     BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  parent_id   BIGINT       DEFAULT 0,
  ancestors   VARCHAR(500) DEFAULT '',
  dept_name   VARCHAR(30)  DEFAULT '',
  order_num   INT          DEFAULT 0,
  leader      BIGINT       DEFAULT NULL,
  status      CHAR(1)      DEFAULT '0',
  del_flag    CHAR(1)      DEFAULT '0',
  create_dept BIGINT       DEFAULT NULL,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (dept_id)
);

CREATE TABLE sys_role (
  role_id              BIGINT       NOT NULL,
  tenant_id            VARCHAR(20)  DEFAULT '000000',
  role_name            VARCHAR(30)  NOT NULL,
  role_key             VARCHAR(100) NOT NULL,
  role_sort            INT          DEFAULT 0,
  data_scope           CHAR(1)      DEFAULT '1',
  menu_check_strictly  TINYINT      DEFAULT 1,
  dept_check_strictly  TINYINT      DEFAULT 1,
  status               CHAR(1)      DEFAULT '0',
  del_flag             CHAR(1)      DEFAULT '0',
  create_dept          BIGINT       DEFAULT NULL,
  create_by            BIGINT       DEFAULT NULL,
  create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by            BIGINT       DEFAULT NULL,
  update_time          TIMESTAMP    DEFAULT NULL,
  remark               VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (role_id)
);

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE oa_employee (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  user_id            BIGINT       DEFAULT NULL,
  employee_no        VARCHAR(32)  NOT NULL,
  name               VARCHAR(64)  NOT NULL,
  gender             CHAR(1)      DEFAULT '2',
  birth_date         DATE         DEFAULT NULL,
  id_card_no         VARCHAR(18)  DEFAULT NULL,
  phone              VARCHAR(11)  DEFAULT NULL,
  email              VARCHAR(50)  DEFAULT NULL,
  dept_id            BIGINT       NOT NULL,
  post_id            BIGINT       DEFAULT NULL,
  position_level     VARCHAR(32)  DEFAULT NULL,
  direct_leader_id   BIGINT       DEFAULT NULL,
  status             VARCHAR(20)  DEFAULT 'DRAFT',
  entry_date         DATE         DEFAULT NULL,
  probation_end_date DATE         DEFAULT NULL,
  regular_date       DATE         DEFAULT NULL,
  leave_date         DATE         DEFAULT NULL,
  workflow_instance_id VARCHAR(64) DEFAULT NULL,
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_employee_no ON oa_employee (employee_no);
CREATE UNIQUE INDEX uk_employee_user ON oa_employee (user_id);

CREATE TABLE oa_employee_history (
  id                 BIGINT        NOT NULL,
  employee_id        BIGINT        NOT NULL,
  event_id           VARCHAR(64)   NOT NULL,
  event_type         VARCHAR(32)   NOT NULL,
  from_status        VARCHAR(20)   DEFAULT NULL,
  to_status          VARCHAR(20)   DEFAULT NULL,
  detail             VARCHAR(1000) DEFAULT NULL,
  workflow_instance_id VARCHAR(64) DEFAULT NULL,
  operator_user_id   BIGINT        NOT NULL,
  operate_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_history_event ON oa_employee_history (event_id);

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

CREATE TABLE oa_onboarding (
  id                 BIGINT      NOT NULL,
  employee_id        BIGINT      NOT NULL,
  event_id           VARCHAR(64) NOT NULL,
  entry_date         DATE        NOT NULL,
  probation_end_date DATE        DEFAULT NULL,
  dept_id            BIGINT      NOT NULL,
  post_id            BIGINT      DEFAULT NULL,
  status             VARCHAR(20) DEFAULT 'COMPLETED',
  workflow_instance_id VARCHAR(64) DEFAULT NULL,
  operator_user_id   BIGINT      DEFAULT NULL,
  operate_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  create_time        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_onboarding_employee ON oa_onboarding (employee_id);
CREATE UNIQUE INDEX uk_onboarding_event ON oa_onboarding (event_id);

CREATE TABLE oa_offboarding (
  id                 BIGINT      NOT NULL,
  employee_id        BIGINT      NOT NULL,
  event_id           VARCHAR(64) NOT NULL,
  reason             VARCHAR(500) DEFAULT NULL,
  last_working_day   DATE        NOT NULL,
  account_frozen     TINYINT     DEFAULT 0,
  status             VARCHAR(20) DEFAULT 'COMPLETED',
  workflow_instance_id VARCHAR(64) DEFAULT NULL,
  operator_user_id   BIGINT      DEFAULT NULL,
  operate_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  create_time        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_offboarding_employee ON oa_offboarding (employee_id);
CREATE UNIQUE INDEX uk_offboarding_event ON oa_offboarding (event_id);

CREATE TABLE oa_job_position (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  position_code VARCHAR(32)  NOT NULL,
  position_name VARCHAR(50)  NOT NULL,
  position_level VARCHAR(32) DEFAULT NULL,
  position_sort INT          DEFAULT 0,
  status        CHAR(1)      DEFAULT '0',
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_position_code ON oa_job_position (position_code);

CREATE TABLE sys_outbox (
  id           BIGINT        NOT NULL GENERATED BY DEFAULT AS IDENTITY,
  event_id     VARCHAR(64)   NOT NULL,
  receiver_id  BIGINT        DEFAULT NULL,
  event_type   VARCHAR(32)   NOT NULL,
  payload      CLOB          NOT NULL,
  status       VARCHAR(16)   DEFAULT 'PENDING',
  retry_count  INT           DEFAULT 0,
  next_attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_error   VARCHAR(500)  DEFAULT NULL,
  create_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  processed_time TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_outbox_event ON sys_outbox (event_id);

CREATE TABLE oa_flow_category (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  code        VARCHAR(32)  NOT NULL,
  name        VARCHAR(64)  NOT NULL,
  sort        INT          DEFAULT 0,
  status      CHAR(1)      DEFAULT '0',
  create_dept BIGINT       DEFAULT NULL,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  remark      VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_category_code ON oa_flow_category (code);

CREATE TABLE oa_flow_definition (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  process_key        VARCHAR(64)  NOT NULL,
  process_name       VARCHAR(64)  NOT NULL,
  category_id        BIGINT       NOT NULL,
  form_key           VARCHAR(64)  NOT NULL,
  business_type      VARCHAR(32)  NOT NULL,
  current_version_no INT          DEFAULT 1,
  status             VARCHAR(16)  DEFAULT 'DRAFT',
  icon               VARCHAR(64)  DEFAULT NULL,
  sort               INT          DEFAULT 0,
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_def_key ON oa_flow_definition (process_key);

CREATE TABLE oa_flow_definition_version (
  id                     BIGINT       NOT NULL,
  tenant_id              VARCHAR(20)  DEFAULT '000000',
  definition_id          BIGINT       NOT NULL,
  version_no             INT          NOT NULL,
  bpmn_resource          VARCHAR(255) NOT NULL,
  bpmn_xml               CLOB         DEFAULT NULL,
  chain_json             CLOB         DEFAULT NULL,
  bpmn_digest            CHAR(64)     NOT NULL,
  validation_summary     VARCHAR(500) DEFAULT NULL,
  status                 VARCHAR(16)  DEFAULT 'DRAFT',
  flowable_proc_def_id   VARCHAR(64)  DEFAULT NULL,
  flowable_deployment_id VARCHAR(64)  DEFAULT NULL,
  published_by           BIGINT       DEFAULT NULL,
  published_time         TIMESTAMP    DEFAULT NULL,
  create_time            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time            TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_def_version ON oa_flow_definition_version (definition_id, version_no);

CREATE TABLE oa_flow_form_version (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  form_key      VARCHAR(64)  NOT NULL,
  form_name     VARCHAR(64)  NOT NULL,
  version_no    INT          NOT NULL,
  schema_json   CLOB         NOT NULL,
  schema_digest CHAR(64)     NOT NULL,
  status        VARCHAR(16)  DEFAULT 'DRAFT',
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_form_version ON oa_flow_form_version (form_key, version_no);

CREATE TABLE oa_flow_instance (
  id                    BIGINT       NOT NULL,
  tenant_id             VARCHAR(20)  DEFAULT '000000',
  definition_id         BIGINT       NOT NULL,
  definition_version_id BIGINT       NOT NULL,
  form_version_id       BIGINT       NOT NULL,
  form_schema_snapshot  CLOB         NOT NULL,
  form_data             CLOB         DEFAULT NULL,
  business_type         VARCHAR(32)  NOT NULL,
  business_id           BIGINT       NOT NULL,
  business_key          VARCHAR(64)  DEFAULT NULL,
  submission_no         INT          DEFAULT 1,
  title                 VARCHAR(255) DEFAULT NULL,
  initiator_user_id     BIGINT       NOT NULL,
  initiator_name        VARCHAR(64)  DEFAULT NULL,
  initiator_dept_id     BIGINT       DEFAULT NULL,
  priority              TINYINT      DEFAULT 0,
  status                TINYINT      DEFAULT 1,
  flowable_proc_inst_id VARCHAR(64)  DEFAULT NULL,
  current_task_name     VARCHAR(64)  DEFAULT NULL,
  current_assignees     VARCHAR(512) DEFAULT NULL,
  start_time            TIMESTAMP    DEFAULT NULL,
  end_time              TIMESTAMP    DEFAULT NULL,
  duration              BIGINT       DEFAULT NULL,
  lock_version          INT          DEFAULT 0,
  create_dept           BIGINT       DEFAULT NULL,
  create_by             BIGINT       DEFAULT NULL,
  create_time           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by             BIGINT       DEFAULT NULL,
  update_time           TIMESTAMP    DEFAULT NULL,
  remark                VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_inst_proc ON oa_flow_instance (flowable_proc_inst_id);
CREATE UNIQUE INDEX uk_flow_inst_biz ON oa_flow_instance (business_type, business_id, submission_no);

CREATE TABLE oa_flow_task_action (
  id                BIGINT        NOT NULL,
  tenant_id         VARCHAR(20)   DEFAULT '000000',
  instance_id       BIGINT        NOT NULL,
  flowable_task_id  VARCHAR(64)   DEFAULT NULL,
  task_name         VARCHAR(64)   DEFAULT NULL,
  task_def_key      VARCHAR(64)   DEFAULT NULL,
  action            VARCHAR(16)   NOT NULL,
  operator_user_id  BIGINT        NOT NULL,
  operator_name     VARCHAR(64)   DEFAULT NULL,
  old_assignee_id   BIGINT        DEFAULT NULL,
  old_assignee_name VARCHAR(64)   DEFAULT NULL,
  new_assignee_id   BIGINT        DEFAULT NULL,
  new_assignee_name VARCHAR(64)   DEFAULT NULL,
  comment           VARCHAR(1000) DEFAULT NULL,
  action_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_flow_business_ref (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  business_type VARCHAR(32)  NOT NULL,
  business_id   BIGINT       NOT NULL,
  instance_id   BIGINT       NOT NULL,
  submission_no INT          DEFAULT 1,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_biz_ref ON oa_flow_business_ref (business_type, business_id, submission_no);

CREATE TABLE oa_flow_idempotency (
  id           BIGINT       NOT NULL,
  idem_key     VARCHAR(64)  NOT NULL,
  user_id      BIGINT       NOT NULL,
  request_path VARCHAR(255) NOT NULL,
  body_digest  CHAR(64)     NOT NULL,
  result_ref   VARCHAR(64)  DEFAULT NULL,
  create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_flow_idem_user_key ON oa_flow_idempotency (user_id, idem_key);

CREATE TABLE oa_leave_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  user_id          BIGINT       NOT NULL,
  employee_id      BIGINT       DEFAULT NULL,
  leave_type       VARCHAR(32)  NOT NULL,
  start_time       TIMESTAMP    NOT NULL,
  end_time         TIMESTAMP    NOT NULL,
  duration_minutes INT          DEFAULT 0,
  reason           VARCHAR(500) DEFAULT NULL,
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  create_dept      BIGINT       DEFAULT NULL,
  create_by        BIGINT       DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT       DEFAULT NULL,
  update_time      TIMESTAMP    DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_overtime_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  user_id          BIGINT       NOT NULL,
  employee_id      BIGINT       DEFAULT NULL,
  overtime_date    DATE         NOT NULL,
  overtime_type    VARCHAR(32)  DEFAULT 'weekday',
  start_time       TIMESTAMP    NOT NULL,
  end_time         TIMESTAMP    NOT NULL,
  duration_minutes INT          DEFAULT 0,
  reason           VARCHAR(255) DEFAULT NULL,
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  create_dept      BIGINT       DEFAULT NULL,
  create_by        BIGINT       DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT       DEFAULT NULL,
  update_time      TIMESTAMP    DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_correction_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  user_id          BIGINT       NOT NULL,
  employee_id      BIGINT       DEFAULT NULL,
  attendance_date  DATE         NOT NULL,
  punch_type       TINYINT      NOT NULL,
  corrected_time   TIMESTAMP    DEFAULT NULL,
  reason           VARCHAR(500) NOT NULL,
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  create_dept      BIGINT       DEFAULT NULL,
  create_by        BIGINT       DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT       DEFAULT NULL,
  update_time      TIMESTAMP    DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

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
  lock_version     INT           DEFAULT 0,
  paid_amount      DECIMAL(12,2) DEFAULT 0.00,
  create_dept      BIGINT        DEFAULT NULL,
  create_by        BIGINT        DEFAULT NULL,
  create_time      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT        DEFAULT NULL,
  update_time      TIMESTAMP     DEFAULT NULL,
  remark           VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_reimburse_no ON oa_reimburse_request (reimburse_no);

CREATE TABLE oa_lifecycle_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  employee_id      BIGINT       NOT NULL,
  user_id          BIGINT       DEFAULT NULL,
  request_type     VARCHAR(16)  NOT NULL,
  effective_date   DATE         NOT NULL,
  form_data        CLOB         DEFAULT NULL,
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  create_dept      BIGINT       DEFAULT NULL,
  create_by        BIGINT       DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT       DEFAULT NULL,
  update_time      TIMESTAMP    DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

INSERT INTO oa_flow_category VALUES(100, '000000', 'HR', '人事流程', 1, '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO oa_flow_category VALUES(101, '000000', 'FINANCE', '财务流程', 2, '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO oa_flow_category VALUES(102, '000000', 'ATTENDANCE', '考勤流程', 3, '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO oa_flow_category VALUES(103, '000000', 'OTHER', '其他', 4, '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO oa_flow_category VALUES(104, '000000', 'ADMIN', '行政办公', 5, '0', null, null, CURRENT_TIMESTAMP, null, null, null);

INSERT INTO sys_role VALUES(20, '000000', '人力资源', 'hr', 2, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(21, '000000', '部门经理', 'dept_manager', 3, '4', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(22, '000000', '普通员工', 'employee', 4, '5', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(23, '000000', '财务', 'finance', 5, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(24, '000000', '出纳', 'cashier', 6, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(25, '000000', '总监', 'director', 7, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(26, '000000', 'admin', 'admin', 8, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);
INSERT INTO sys_role VALUES(27, '000000', 'gm',    'gm',    9, '1', 1, 1, '0', '0', null, null, CURRENT_TIMESTAMP, null, null, null);

ALTER TABLE oa_flow_instance ADD COLUMN is_timeout TINYINT DEFAULT 0;

CREATE TABLE oa_flow_cc (
  id                  BIGINT       NOT NULL,
  tenant_id           VARCHAR(20)  DEFAULT '000000',
  instance_id         BIGINT       NOT NULL,
  sender_user_id      BIGINT       NOT NULL,
  cc_user_id          BIGINT       NOT NULL,
  comment             VARCHAR(500) DEFAULT NULL,
  event_id            VARCHAR(64)  NOT NULL,
  read_time           TIMESTAMP    DEFAULT NULL,
  create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cc_event ON oa_flow_cc (event_id);

CREATE TABLE oa_flow_delegate (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  owner_id       BIGINT       NOT NULL,
  delegate_id    BIGINT       NOT NULL,
  start_date     DATE         NOT NULL,
  end_date       DATE         NOT NULL,
  process_keys   VARCHAR(255) DEFAULT NULL,
  status         TINYINT      DEFAULT 1,
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE oa_employee ADD COLUMN base_salary VARCHAR(512) DEFAULT NULL;
ALTER TABLE oa_employee ADD COLUMN salary_key_version VARCHAR(16) DEFAULT NULL;

ALTER TABLE oa_flow_cc ADD COLUMN create_dept BIGINT DEFAULT NULL;
ALTER TABLE oa_flow_cc ADD COLUMN create_by BIGINT DEFAULT NULL;
ALTER TABLE oa_flow_cc ADD COLUMN update_by BIGINT DEFAULT NULL;
ALTER TABLE oa_flow_cc ADD COLUMN update_time TIMESTAMP DEFAULT NULL;

ALTER TABLE oa_reimburse_request ADD COLUMN budget_id BIGINT DEFAULT NULL;

CREATE TABLE oa_flow_generic_request (
  id               BIGINT       NOT NULL,
  tenant_id        VARCHAR(20)  DEFAULT '000000',
  definition_id    BIGINT       NOT NULL,
  user_id          BIGINT       NOT NULL,
  title            VARCHAR(255) DEFAULT NULL,
  form_data        CLOB         DEFAULT NULL,
  status           TINYINT      DEFAULT 1,
  flow_instance_id BIGINT       DEFAULT NULL,
  submission_no    INT          DEFAULT 0,
  lock_version     INT          DEFAULT 0,
  create_dept      BIGINT       DEFAULT NULL,
  create_by        BIGINT       DEFAULT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by        BIGINT       DEFAULT NULL,
  update_time      TIMESTAMP    DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
