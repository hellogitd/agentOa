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
CREATE UNIQUE INDEX uk_employee_user ON oa_employee (user_id);

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

CREATE TABLE oa_shift (
  id                 BIGINT       NOT NULL,
  tenant_id          VARCHAR(20)  DEFAULT '000000',
  shift_code         VARCHAR(64)  NOT NULL,
  shift_name         VARCHAR(64)  NOT NULL,
  work_start_time    TIME         NOT NULL,
  work_end_time      TIME         NOT NULL,
  rest_start_time    TIME         DEFAULT NULL,
  rest_end_time      TIME         DEFAULT NULL,
  is_cross_day      TINYINT      DEFAULT 0,
  flexible_minutes   INT          DEFAULT 0,
  grace_minutes      INT          DEFAULT 0,
  punch_window_start INT          DEFAULT 120,
  punch_window_end   INT          DEFAULT 240,
  status             CHAR(1)      DEFAULT '0',
  create_dept        BIGINT       DEFAULT NULL,
  create_by          BIGINT       DEFAULT NULL,
  create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by          BIGINT       DEFAULT NULL,
  update_time        TIMESTAMP    DEFAULT NULL,
  remark             VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_shift_code ON oa_shift (shift_code);

CREATE TABLE oa_attendance_group (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  group_code      VARCHAR(64)  NOT NULL,
  group_name      VARCHAR(64)  NOT NULL,
  shift_id        BIGINT       NOT NULL,
  work_days       VARCHAR(32)  DEFAULT '1,2,3,4,5',
  effective_date  DATE         NOT NULL,
  scope_snapshot  VARCHAR(2000) DEFAULT NULL,
  status          CHAR(1)      DEFAULT '0',
  create_dept     BIGINT       DEFAULT NULL,
  create_by       BIGINT       DEFAULT NULL,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT       DEFAULT NULL,
  update_time     TIMESTAMP    DEFAULT NULL,
  remark          VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_group_code ON oa_attendance_group (group_code);

CREATE TABLE oa_attendance_member (
  id            BIGINT      NOT NULL,
  tenant_id     VARCHAR(20) DEFAULT '000000',
  group_id      BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  employee_id   BIGINT      DEFAULT NULL,
  valid_from    DATE        NOT NULL,
  valid_to      DATE        DEFAULT NULL,
  create_by     BIGINT      DEFAULT NULL,
  create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_member_user_from ON oa_attendance_member (user_id, valid_from);

CREATE TABLE oa_calendar (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  work_date     DATE         NOT NULL,
  day_type      CHAR(1)      DEFAULT '0',
  description   VARCHAR(128) DEFAULT NULL,
  rule_version  INT          DEFAULT 1,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_calendar_date ON oa_calendar (work_date);

CREATE TABLE oa_holiday (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  holiday_date  DATE         NOT NULL,
  holiday_name  VARCHAR(64)  NOT NULL,
  holiday_type  VARCHAR(16)  DEFAULT 'HOLIDAY',
  year          INT          NOT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_holiday_date ON oa_holiday (holiday_date);

CREATE TABLE oa_punch_record (
  id                    BIGINT        NOT NULL,
  tenant_id             VARCHAR(20)   DEFAULT '000000',
  user_id               BIGINT        NOT NULL,
  employee_id           BIGINT        DEFAULT NULL,
  punch_date            DATE          NOT NULL,
  punch_time            TIMESTAMP     NOT NULL,
  punch_type            TINYINT       NOT NULL,
  correction_request_id BIGINT        DEFAULT NULL,
  is_late               TINYINT       DEFAULT 0,
  late_minutes          INT           DEFAULT 0,
  is_early              TINYINT       DEFAULT 0,
  early_minutes         INT           DEFAULT 0,
  lng                   DECIMAL(10,6) DEFAULT NULL,
  lat                   DECIMAL(10,6) DEFAULT NULL,
  accuracy_meters       DECIMAL(10,2) DEFAULT NULL,
  address               VARCHAR(255)  DEFAULT NULL,
  device                VARCHAR(64)   DEFAULT NULL,
  ip                    VARCHAR(50)   DEFAULT NULL,
  source                TINYINT       DEFAULT 1,
  create_time           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_punch_correction ON oa_punch_record (correction_request_id);
CREATE INDEX idx_punch_user_date ON oa_punch_record (user_id, punch_date);

CREATE TABLE oa_attendance_day (
  id                BIGINT       NOT NULL,
  tenant_id         VARCHAR(20)  DEFAULT '000000',
  user_id           BIGINT       NOT NULL,
  employee_id       BIGINT       DEFAULT NULL,
  dept_id           BIGINT       DEFAULT NULL,
  attendance_date   DATE         NOT NULL,
  group_id          BIGINT       DEFAULT NULL,
  shift_id          BIGINT       DEFAULT NULL,
  rule_version      INT          DEFAULT 1,
  first_punch_time  TIMESTAMP    DEFAULT NULL,
  last_punch_time   TIMESTAMP    DEFAULT NULL,
  work_status       TINYINT      DEFAULT 0,
  missing_punch     TINYINT      DEFAULT 0,
  scheduled_minutes INT          DEFAULT 0,
  worked_minutes    INT          DEFAULT 0,
  late_minutes      INT          DEFAULT 0,
  early_minutes     INT          DEFAULT 0,
  leave_minutes     INT          DEFAULT 0,
  overtime_minutes  INT          DEFAULT 0,
  is_abnormal       TINYINT      DEFAULT 0,
  abnormal_reason   VARCHAR(255) DEFAULT NULL,
  create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_day_user_date ON oa_attendance_day (user_id, attendance_date);

CREATE TABLE oa_leave_type (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  type_code      VARCHAR(32)  NOT NULL,
  type_name      VARCHAR(64)  NOT NULL,
  quota_limited  TINYINT      DEFAULT 1,
  default_minutes INT         DEFAULT 0,
  status         CHAR(1)      DEFAULT '0',
  create_dept    BIGINT       DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_leave_type_code ON oa_leave_type (type_code);

CREATE TABLE oa_leave_balance (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  user_id        BIGINT       NOT NULL,
  employee_id    BIGINT       DEFAULT NULL,
  year           INT          NOT NULL,
  leave_type     VARCHAR(32)  NOT NULL,
  total_minutes  INT          DEFAULT 0,
  frozen_minutes INT          DEFAULT 0,
  used_minutes   INT          DEFAULT 0,
  lock_version   INT          DEFAULT 0,
  expire_date    DATE         DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_balance_user_year_type ON oa_leave_balance (user_id, year, leave_type);

CREATE TABLE oa_leave_ledger (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  balance_id     BIGINT       NOT NULL,
  event_key      VARCHAR(128) NOT NULL,
  business_type  VARCHAR(32)  NOT NULL,
  business_id    BIGINT       NOT NULL,
  submission_no  INT          DEFAULT 0,
  action         VARCHAR(16)  NOT NULL,
  total_delta    INT          DEFAULT 0,
  frozen_delta   INT          DEFAULT 0,
  used_delta     INT          DEFAULT 0,
  leave_minutes  INT          DEFAULT 0,
  operator_id    BIGINT       NOT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ledger_event ON oa_leave_ledger (balance_id, event_key);

CREATE TABLE oa_overtime (
  id                  BIGINT       NOT NULL,
  tenant_id           VARCHAR(20)  DEFAULT '000000',
  user_id             BIGINT       NOT NULL,
  employee_id         BIGINT       DEFAULT NULL,
  overtime_request_id BIGINT       NOT NULL,
  overtime_date       DATE         NOT NULL,
  start_time          TIMESTAMP    DEFAULT NULL,
  end_time            TIMESTAMP    DEFAULT NULL,
  duration_minutes    INT          DEFAULT 0,
  overtime_type       VARCHAR(32)  DEFAULT NULL,
  status              TINYINT      DEFAULT 3,
  create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time         TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_overtime_request ON oa_overtime (overtime_request_id);

CREATE TABLE oa_correction (
  id                   BIGINT       NOT NULL,
  tenant_id            VARCHAR(20)  DEFAULT '000000',
  user_id              BIGINT       NOT NULL,
  employee_id          BIGINT       DEFAULT NULL,
  correction_request_id BIGINT      NOT NULL,
  attendance_date      DATE         NOT NULL,
  punch_type           TINYINT      NOT NULL,
  corrected_time       TIMESTAMP    NOT NULL,
  status               TINYINT      DEFAULT 3,
  create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_correction_request ON oa_correction (correction_request_id);

ALTER TABLE oa_punch_record ADD COLUMN photo_file_id BIGINT DEFAULT NULL;
ALTER TABLE oa_punch_record ADD COLUMN wifi_name VARCHAR(64) DEFAULT NULL;

CREATE TABLE oa_shift_assignment (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  user_id       BIGINT       NOT NULL,
  employee_id   BIGINT       DEFAULT NULL,
  shift_id      BIGINT       NOT NULL,
  work_date     DATE         NOT NULL,
  source        TINYINT      DEFAULT 1,
  create_dept   BIGINT       DEFAULT NULL,
  create_by     BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT       DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_shift_assignment ON oa_shift_assignment (user_id, work_date);

CREATE TABLE oa_leave_batch (
  id              BIGINT       NOT NULL,
  tenant_id       VARCHAR(20)  DEFAULT '000000',
  user_id         BIGINT       NOT NULL,
  year            INT          NOT NULL,
  leave_type      VARCHAR(32)  NOT NULL,
  batch_no        VARCHAR(64)  NOT NULL,
  grant_minutes   INT          DEFAULT 0,
  frozen_minutes  INT          DEFAULT 0,
  used_minutes    INT          DEFAULT 0,
  expired_minutes INT          DEFAULT 0,
  valid_from      DATE         DEFAULT NULL,
  expire_date     DATE         DEFAULT NULL,
  event_key       VARCHAR(128) NOT NULL,
  status          TINYINT      DEFAULT 1,
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_batch_event ON oa_leave_batch (event_key);
CREATE INDEX idx_batch_user_type ON oa_leave_batch (user_id, leave_type, expire_date);
CREATE INDEX idx_batch_due ON oa_leave_batch (status, expire_date);

CREATE TABLE oa_leave_batch_allocation (
  id               BIGINT       NOT NULL,
  batch_id         BIGINT       NOT NULL,
  ledger_event_key VARCHAR(128) NOT NULL,
  minutes          INT          NOT NULL,
  action           VARCHAR(16)  NOT NULL,
  create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX idx_alloc_batch ON oa_leave_batch_allocation (batch_id);
CREATE INDEX idx_alloc_event ON oa_leave_batch_allocation (ledger_event_key);
