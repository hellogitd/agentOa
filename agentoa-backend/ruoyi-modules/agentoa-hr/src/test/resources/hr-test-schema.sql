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
  position_code VARCHAR(64)  NOT NULL,
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

ALTER TABLE oa_employee ADD COLUMN base_salary VARCHAR(512) DEFAULT NULL;
ALTER TABLE oa_employee ADD COLUMN salary_key_version VARCHAR(16) DEFAULT NULL;

CREATE TABLE oa_employee_education (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  employee_id   BIGINT       NOT NULL,
  school        VARCHAR(128) DEFAULT NULL,
  major         VARCHAR(128) DEFAULT NULL,
  education     VARCHAR(32)  DEFAULT NULL,
  degree        VARCHAR(32)  DEFAULT NULL,
  start_date    DATE         DEFAULT NULL,
  end_date      DATE         DEFAULT NULL,
  is_full_time  TINYINT      DEFAULT 1,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_employee_work (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  employee_id   BIGINT       NOT NULL,
  company       VARCHAR(128) DEFAULT NULL,
  position      VARCHAR(64)  DEFAULT NULL,
  start_date    DATE         DEFAULT NULL,
  end_date      DATE         DEFAULT NULL,
  leave_reason  VARCHAR(255) DEFAULT NULL,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_employee_change (
  id                  BIGINT       NOT NULL,
  tenant_id           VARCHAR(20)  DEFAULT '000000',
  employee_id         BIGINT       NOT NULL,
  change_type         TINYINT      NOT NULL,
  effective_date      DATE         NOT NULL,
  old_dept_id         BIGINT       DEFAULT NULL,
  new_dept_id         BIGINT       DEFAULT NULL,
  old_post_id         BIGINT       DEFAULT NULL,
  new_post_id         BIGINT       DEFAULT NULL,
  old_position_level  VARCHAR(32)  DEFAULT NULL,
  new_position_level  VARCHAR(32)  DEFAULT NULL,
  old_salary          VARCHAR(512) DEFAULT NULL,
  new_salary          VARCHAR(512) DEFAULT NULL,
  salary_key_version  VARCHAR(16)  DEFAULT NULL,
  reason              VARCHAR(255) DEFAULT NULL,
  applied             TINYINT      DEFAULT 0,
  event_id            VARCHAR(64)  DEFAULT NULL,
  flow_instance_id    VARCHAR(64)  DEFAULT NULL,
  source_request_id   BIGINT       DEFAULT NULL,
  create_dept         BIGINT       DEFAULT NULL,
  create_by           BIGINT       DEFAULT NULL,
  create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by           BIGINT       DEFAULT NULL,
  update_time         TIMESTAMP    DEFAULT NULL,
  remark              VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_change_source_request ON oa_employee_change (source_request_id);

CREATE TABLE oa_contract (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  employee_id    BIGINT       NOT NULL,
  contract_no    VARCHAR(64)  DEFAULT NULL,
  contract_type  VARCHAR(32)  DEFAULT 'FIXED_TERM',
  start_date     DATE         NOT NULL,
  end_date       DATE         DEFAULT NULL,
  sign_date      DATE         DEFAULT NULL,
  renew_count    INT          DEFAULT 0,
  status         TINYINT      DEFAULT 1,
  file_id        BIGINT       DEFAULT NULL,
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_contract_no ON oa_contract (contract_no);
