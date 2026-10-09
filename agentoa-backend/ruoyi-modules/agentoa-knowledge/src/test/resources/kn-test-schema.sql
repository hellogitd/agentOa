-- H2 (MODE=MySQL) schema for agentoa-knowledge tests. Mirrors V9 + referenced sys_* tables.

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

CREATE TABLE sys_file (
  id            BIGINT        NOT NULL,
  bucket        VARCHAR(64)   NOT NULL,
  object_key    VARCHAR(255)  NOT NULL,
  original_name VARCHAR(255)  DEFAULT NULL,
  content_type  VARCHAR(128)  DEFAULT NULL,
  size_bytes    BIGINT        DEFAULT NULL,
  sha256        CHAR(64)      DEFAULT NULL,
  owner_user_id BIGINT        DEFAULT NULL,
  create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_file_object ON sys_file (bucket, object_key);

CREATE TABLE oa_knowledge_space (
  id          BIGINT        NOT NULL,
  tenant_id   VARCHAR(20)   DEFAULT '000000',
  name        VARCHAR(128)  NOT NULL,
  icon        VARCHAR(64)   DEFAULT NULL,
  description VARCHAR(500)  DEFAULT NULL,
  space_type  TINYINT       DEFAULT 1,
  create_dept BIGINT        DEFAULT NULL,
  create_by   BIGINT        DEFAULT NULL,
  create_time TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by   BIGINT        DEFAULT NULL,
  update_time TIMESTAMP     DEFAULT NULL,
  remark      VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_knowledge_member (
  id           BIGINT      NOT NULL,
  tenant_id    VARCHAR(20) DEFAULT '000000',
  space_id     BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  role         VARCHAR(16) NOT NULL,
  granted_by   BIGINT      DEFAULT NULL,
  granted_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  create_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_member ON oa_knowledge_member (space_id, user_id);

CREATE TABLE oa_document (
  id             BIGINT        NOT NULL,
  tenant_id      VARCHAR(20)   DEFAULT '000000',
  space_id       BIGINT        NOT NULL,
  parent_id      BIGINT        DEFAULT 0,
  node_type      TINYINT       DEFAULT 1,
  title          VARCHAR(255)  NOT NULL,
  content        CLOB          DEFAULT NULL,
  content_text   CLOB          DEFAULT NULL,
  doc_type       VARCHAR(32)   DEFAULT 'markdown',
  tags           VARCHAR(255)  DEFAULT NULL,
  version        INT           DEFAULT 1,
  status         TINYINT       DEFAULT 1,
  is_top         TINYINT       DEFAULT 0,
  view_count     INT           DEFAULT 0,
  deleted_at     TIMESTAMP     DEFAULT NULL,
  deleted_by     BIGINT        DEFAULT NULL,
  last_edit_by   BIGINT        DEFAULT NULL,
  last_edit_time TIMESTAMP     DEFAULT NULL,
  create_dept    BIGINT        DEFAULT NULL,
  create_by      BIGINT        DEFAULT NULL,
  create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT        DEFAULT NULL,
  update_time    TIMESTAMP     DEFAULT NULL,
  remark         VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE oa_document_version (
  id             BIGINT        NOT NULL,
  tenant_id      VARCHAR(20)   DEFAULT '000000',
  document_id    BIGINT        NOT NULL,
  version        INT           NOT NULL,
  title          VARCHAR(255)  DEFAULT NULL,
  content        CLOB          DEFAULT NULL,
  change_summary VARCHAR(255)  DEFAULT NULL,
  create_by      BIGINT        DEFAULT NULL,
  create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_document_version ON oa_document_version (document_id, version);

CREATE TABLE oa_document_revision (
  id           BIGINT       NOT NULL,
  tenant_id    VARCHAR(20)  DEFAULT '000000',
  document_id  BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  base_version INT          NOT NULL,
  title        VARCHAR(255) DEFAULT NULL,
  content      CLOB         DEFAULT NULL,
  update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_document_revision ON oa_document_revision (document_id, user_id);

CREATE TABLE oa_document_acl (
  id           BIGINT      NOT NULL,
  tenant_id    VARCHAR(20) DEFAULT '000000',
  document_id  BIGINT      NOT NULL,
  subject_type VARCHAR(8)  NOT NULL,
  subject_id   BIGINT      NOT NULL,
  role         VARCHAR(16) NOT NULL,
  override_only  TINYINT       DEFAULT 0,
  granted_by   BIGINT      DEFAULT NULL,
  granted_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_document_acl ON oa_document_acl (document_id, subject_type, subject_id);

CREATE TABLE oa_document_file (
  id                 BIGINT        NOT NULL,
  tenant_id          VARCHAR(20)   DEFAULT '000000',
  space_id           BIGINT        NOT NULL,
  document_id        BIGINT        DEFAULT NULL,
  file_id            BIGINT        NOT NULL,
  file_name          VARCHAR(255)  NOT NULL,
  file_ext           VARCHAR(32)   DEFAULT NULL,
  file_size          BIGINT        DEFAULT NULL,
  content_type       VARCHAR(128)  DEFAULT NULL,
  download_count     INT           DEFAULT 0,
  deleted_at         TIMESTAMP     DEFAULT NULL,
  deleted_by         BIGINT        DEFAULT NULL,
  last_download_by   BIGINT        DEFAULT NULL,
  last_download_time TIMESTAMP     DEFAULT NULL,
  create_dept        BIGINT        DEFAULT NULL,
  create_by          BIGINT        DEFAULT NULL,
  create_time        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE oa_document_comment (
  id            BIGINT        NOT NULL,
  tenant_id     VARCHAR(20)   DEFAULT '000000',
  document_id   BIGINT        NOT NULL,
  parent_id     BIGINT        DEFAULT 0,
  content       VARCHAR(1000) NOT NULL,
  create_dept   BIGINT        DEFAULT NULL,
  create_by     BIGINT        DEFAULT NULL,
  create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT        DEFAULT NULL,
  update_time   TIMESTAMP     DEFAULT NULL,
  remark        VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_comment_document ON oa_document_comment (document_id, create_time);

CREATE TABLE oa_document_like (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  document_id   BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_document_like ON oa_document_like (document_id, user_id);

CREATE TABLE oa_document_favorite (
  id            BIGINT       NOT NULL,
  tenant_id     VARCHAR(20)  DEFAULT '000000',
  document_id   BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_document_favorite ON oa_document_favorite (document_id, user_id);
