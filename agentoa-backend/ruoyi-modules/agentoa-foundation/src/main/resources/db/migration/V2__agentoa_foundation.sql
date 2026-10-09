-- AgentOA single-organization baseline. No usable default password.
DELETE FROM sys_user_role WHERE user_id <> 1;
DELETE FROM sys_user_post WHERE user_id <> 1;
DELETE FROM sys_user WHERE user_id <> 1;
UPDATE sys_user SET nick_name='AgentOA 管理员', email='', phonenumber='', password='!UNINITIALIZED!', status='1' WHERE user_id=1;
UPDATE sys_tenant SET company_name='AgentOA', contact_user_name='管理员', contact_phone='' WHERE tenant_id='000000';
UPDATE sys_client SET grant_type='password', active_timeout=1800, timeout=7200;
UPDATE sys_client SET status='1' WHERE client_id <> 'e5cd7e4891bf95d1d19206ce24a7b32e';
UPDATE sys_config SET config_value='', remark='新建用户必须设置独立密码' WHERE config_key='sys.user.initPassword';
UPDATE sys_config SET config_value='false' WHERE config_key IN ('sys.account.registerUser','sys.oss.previewListResource');
DELETE FROM sys_oss_config;
-- Hide unavailable tenant, external monitor/job and inherited file configuration entries.
UPDATE sys_menu SET status='1' WHERE menu_id IN (6,121,122,120,117,118) OR parent_id IN (6,121,122,120,117,118);

CREATE TABLE oa_bootstrap (
  id INT PRIMARY KEY,
  initialized_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 1;

CREATE TABLE sys_file (
  id BIGINT NOT NULL PRIMARY KEY,
  bucket VARCHAR(64) NOT NULL,
  object_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_object (bucket, object_key),
  KEY idx_owner (owner_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sys_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  receiver_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  next_attempt_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error VARCHAR(500),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_time DATETIME,
  UNIQUE KEY uk_event(event_id),
  KEY idx_dispatch(status,next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE nc_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  receiver_id BIGINT NOT NULL,
  msg_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  is_read TINYINT NOT NULL DEFAULT 0,
  read_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_event_receiver(event_id,receiver_id),
  KEY idx_receiver(receiver_id,is_read,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE oa_flow_probe (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_key VARCHAR(64) NOT NULL UNIQUE,
  process_instance_id VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
