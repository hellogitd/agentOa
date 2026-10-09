-- H2 (MODE=MySQL) schema for agentoa-collaboration tests. Mirrors V10 + referenced V2/V4 tables.

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

CREATE TABLE oa_calendar_event (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  lock_version   INT          DEFAULT 0,
  title          VARCHAR(255) NOT NULL,
  description    CLOB         DEFAULT NULL,
  event_type     INT          DEFAULT 1,
  start_time     TIMESTAMP    NOT NULL,
  end_time       TIMESTAMP    NOT NULL,
  is_all_day     INT          DEFAULT 0,
  location       VARCHAR(255) DEFAULT NULL,
  organizer_id   BIGINT       NOT NULL,
  room_id        BIGINT       DEFAULT NULL,
  visibility     INT          DEFAULT 2,
  remind_minutes INT          DEFAULT 15,
  repeat_rule    VARCHAR(255) DEFAULT NULL,
  series_id      BIGINT       DEFAULT 0,
  repeat_until   TIMESTAMP    DEFAULT NULL,
  repeat_count   INT          DEFAULT NULL,
  is_exception   TINYINT      DEFAULT 0,
  status         INT          DEFAULT 1,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  del_flag       INT          DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE oa_calendar_attendee (
  event_id        BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  response_status VARCHAR(16)  DEFAULT 'PENDING',
  create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (event_id, user_id)
);

CREATE TABLE oa_meeting_room (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(20)  DEFAULT '000000',
  name        VARCHAR(64)  NOT NULL,
  location    VARCHAR(255) DEFAULT NULL,
  capacity    INT          DEFAULT NULL,
  equipment   VARCHAR(255) DEFAULT NULL,
  status      INT          DEFAULT 1,
  create_by   BIGINT       DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT       DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  del_flag    INT          DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE oa_room_booking (
  id           BIGINT       NOT NULL,
  tenant_id    VARCHAR(20)  DEFAULT '000000',
  lock_version INT          DEFAULT 0,
  room_id      BIGINT       NOT NULL,
  event_id     BIGINT       DEFAULT NULL,
  title        VARCHAR(255) NOT NULL,
  booker_id    BIGINT       NOT NULL,
  start_time   TIMESTAMP    NOT NULL,
  end_time     TIMESTAMP    NOT NULL,
  checkin_time TIMESTAMP    DEFAULT NULL,
  status       INT          DEFAULT 1,
  create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time  TIMESTAMP    DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_booking_event ON oa_room_booking (event_id);

CREATE TABLE oa_task (
  id             BIGINT       NOT NULL,
  tenant_id      VARCHAR(20)  DEFAULT '000000',
  lock_version   INT          DEFAULT 0,
  title          VARCHAR(255) NOT NULL,
  description    CLOB         DEFAULT NULL,
  parent_id      BIGINT       DEFAULT NULL,
  assigner_id    BIGINT       NOT NULL,
  assignee_id    BIGINT       NOT NULL,
  priority       INT          DEFAULT 2,
  status         INT          DEFAULT 1,
  start_date     TIMESTAMP    DEFAULT NULL,
  due_date       TIMESTAMP    DEFAULT NULL,
  completed_time TIMESTAMP    DEFAULT NULL,
  progress       INT          DEFAULT 0,
  tags           VARCHAR(255) DEFAULT NULL,
  create_by      BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT       DEFAULT NULL,
  update_time    TIMESTAMP    DEFAULT NULL,
  del_flag       INT          DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE oa_task_member (
  task_id     BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  create_by   BIGINT   DEFAULT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id, user_id)
);

CREATE TABLE oa_task_activity (
  id            BIGINT       NOT NULL,
  task_id       BIGINT       NOT NULL,
  activity_type INT          NOT NULL,
  content       CLOB         DEFAULT NULL,
  operator_id   BIGINT       NOT NULL,
  mention_ids   VARCHAR(500) DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  del_flag      INT          DEFAULT 0,
  PRIMARY KEY (id)
);
