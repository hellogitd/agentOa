-- AgentOA module 1: organization, posts and employee lifecycle.
-- API contract: docs/05-api-spec.md section 3. Business ids are assigned snowflake values.

-- Business uniqueness on the inherited organization tables (rows are unique after V3).
ALTER TABLE sys_dept ADD UNIQUE KEY uk_dept_parent_name (parent_id, dept_name);
ALTER TABLE sys_post ADD UNIQUE KEY uk_post_code (post_code);

CREATE TABLE oa_job_position (
  id            BIGINT        NOT NULL                   COMMENT '岗位ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  position_code VARCHAR(64)   NOT NULL                   COMMENT '岗位编码',
  position_name VARCHAR(50)   NOT NULL                   COMMENT '岗位名称',
  position_level VARCHAR(32)  DEFAULT NULL               COMMENT '默认职级',
  position_sort INT           NOT NULL DEFAULT 0         COMMENT '显示顺序',
  status        CHAR(1)       NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_position_code (position_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务岗位表';

CREATE TABLE oa_employee (
  id                 BIGINT       NOT NULL                   COMMENT '员工档案ID',
  tenant_id          VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id            BIGINT       DEFAULT NULL               COMMENT '关联系统账号',
  employee_no        VARCHAR(32)  NOT NULL                   COMMENT '工号',
  name               VARCHAR(64)  NOT NULL                   COMMENT '姓名',
  gender             CHAR(1)      NOT NULL DEFAULT '2'       COMMENT '性别（0女 1男 2未知）',
  birth_date         DATE         DEFAULT NULL               COMMENT '出生日期',
  id_card_no         VARCHAR(18)  DEFAULT NULL               COMMENT '身份证号',
  phone              VARCHAR(11)  DEFAULT NULL               COMMENT '手机号',
  email              VARCHAR(50)  DEFAULT NULL               COMMENT '邮箱',
  dept_id            BIGINT       NOT NULL                   COMMENT '部门ID',
  post_id            BIGINT       DEFAULT NULL               COMMENT '岗位ID',
  position_level     VARCHAR(32)  DEFAULT NULL               COMMENT '职级',
  direct_leader_id   BIGINT       DEFAULT NULL               COMMENT '直属主管账号ID',
  status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'   COMMENT '员工状态',
  entry_date         DATE         DEFAULT NULL               COMMENT '入职日期',
  probation_end_date DATE         DEFAULT NULL               COMMENT '试用期截止日期',
  regular_date       DATE         DEFAULT NULL               COMMENT '转正日期',
  leave_date         DATE         DEFAULT NULL               COMMENT '离职日期',
  workflow_instance_id VARCHAR(64) DEFAULT NULL              COMMENT '预留流程实例ID',
  create_dept        BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_no (employee_no),
  UNIQUE KEY uk_employee_user (user_id),
  KEY idx_employee_dept (dept_id),
  KEY idx_employee_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案表';

CREATE TABLE oa_employee_history (
  id                 BIGINT       NOT NULL                   COMMENT '历史ID',
  employee_id        BIGINT       NOT NULL                   COMMENT '员工档案ID',
  event_id           VARCHAR(64)  NOT NULL                   COMMENT '业务事件ID（幂等）',
  event_type         VARCHAR(32)  NOT NULL                   COMMENT '事件类型',
  from_status        VARCHAR(20)  DEFAULT NULL               COMMENT '变更前状态',
  to_status          VARCHAR(20)  DEFAULT NULL               COMMENT '变更后状态',
  detail             VARCHAR(1000) DEFAULT NULL              COMMENT '事件明细',
  workflow_instance_id VARCHAR(64) DEFAULT NULL              COMMENT '关联流程实例ID',
  operator_user_id   BIGINT       NOT NULL                   COMMENT '操作人账号ID',
  operate_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_history_event (event_id),
  KEY idx_history_employee (employee_id, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工状态变更历史';

CREATE TABLE oa_onboarding (
  id                 BIGINT       NOT NULL                   COMMENT '入职事件ID',
  employee_id        BIGINT       NOT NULL                   COMMENT '员工档案ID',
  event_id           VARCHAR(64)  NOT NULL                   COMMENT '业务事件ID（幂等）',
  entry_date         DATE         NOT NULL                   COMMENT '入职日期',
  probation_end_date DATE         DEFAULT NULL               COMMENT '试用期截止日期',
  dept_id            BIGINT       NOT NULL                   COMMENT '入职部门',
  post_id            BIGINT       DEFAULT NULL               COMMENT '入职岗位',
  status             VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED' COMMENT '事件状态',
  workflow_instance_id VARCHAR(64) DEFAULT NULL              COMMENT '关联流程实例ID',
  operator_user_id   BIGINT       DEFAULT NULL               COMMENT '操作人账号ID',
  operate_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_onboarding_employee (employee_id),
  UNIQUE KEY uk_onboarding_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入职事件表';

CREATE TABLE oa_offboarding (
  id                 BIGINT       NOT NULL                   COMMENT '离职事件ID',
  employee_id        BIGINT       NOT NULL                   COMMENT '员工档案ID',
  event_id           VARCHAR(64)  NOT NULL                   COMMENT '业务事件ID（幂等）',
  reason             VARCHAR(500) DEFAULT NULL               COMMENT '离职原因',
  last_working_day   DATE         NOT NULL                   COMMENT '最后工作日',
  account_frozen     TINYINT      NOT NULL DEFAULT 0         COMMENT '账号是否已冻结',
  status             VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED' COMMENT '事件状态',
  workflow_instance_id VARCHAR(64) DEFAULT NULL              COMMENT '关联流程实例ID',
  operator_user_id   BIGINT       DEFAULT NULL               COMMENT '操作人账号ID',
  operate_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_offboarding_employee (employee_id),
  UNIQUE KEY uk_offboarding_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职事件表';

-- Idempotency-Key records (scope: user + key, retained 24h, see API spec 1.7).
CREATE TABLE oa_idempotency (
  id            BIGINT       NOT NULL                   COMMENT '记录ID',
  idem_key      VARCHAR(64)  NOT NULL                   COMMENT 'Idempotency-Key',
  user_id       BIGINT       NOT NULL                   COMMENT '调用方账号ID',
  request_path  VARCHAR(255) NOT NULL                   COMMENT '请求路径',
  body_digest   CHAR(64)     NOT NULL                   COMMENT '请求体摘要',
  result_ref    VARCHAR(64)  DEFAULT NULL               COMMENT '首次结果引用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_idem_user_key (user_id, idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写接口幂等记录';

-- Employee status dictionary.
INSERT INTO sys_dict_type VALUES(30, '000000', '员工状态', 'hr_employee_status', 100, 1, sysdate(), null, null, '员工生命周期状态');
INSERT INTO sys_dict_data VALUES(300, '000000', 1, '草稿', 'DRAFT', 'hr_employee_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(301, '000000', 2, '试用期', 'PROBATION', 'hr_employee_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(302, '000000', 3, '正式', 'ACTIVE', 'hr_employee_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(303, '000000', 4, '待离职', 'LEAVE_PENDING', 'hr_employee_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(304, '000000', 5, '已离职', 'LEFT', 'hr_employee_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(305, '000000', 6, '已停用', 'DISABLED', 'hr_employee_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');

-- Roles used by the module 1 permission matrix.
INSERT INTO sys_role VALUES(20, '000000', '人力资源', 'hr', 2, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '组织人事管理');
INSERT INTO sys_role VALUES(21, '000000', '部门经理', 'dept_manager', 3, '4', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '本部门及以下数据范围');
INSERT INTO sys_role VALUES(22, '000000', '普通员工', 'employee', 4, '5', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '仅本人数据范围');

-- HR menus and buttons (permission strings follow docs/05-api-spec.md section 3).
INSERT INTO sys_menu VALUES(2000, '组织人事', 0, 6, 'hr', null, '', 1, 0, 'M', '0', '0', '', 'peoples', 100, 1, sysdate(), null, null, '组织人事目录');
INSERT INTO sys_menu VALUES(2001, '组织架构', 2000, 1, 'dept', 'hr/dept/index', '', 1, 0, 'C', '0', '0', 'hr:dept:list', 'tree-table', 100, 1, sysdate(), null, null, '部门组织树');
INSERT INTO sys_menu VALUES(2002, '岗位管理', 2000, 2, 'post', 'hr/post/index', '', 1, 0, 'C', '0', '0', 'hr:post:list', 'post', 100, 1, sysdate(), null, null, '业务岗位管理');
INSERT INTO sys_menu VALUES(2003, '花名册', 2000, 3, 'employee', 'hr/employee/index', '', 1, 0, 'C', '0', '0', 'hr:employee:list', 'user', 100, 1, sysdate(), null, null, '员工花名册');
INSERT INTO sys_menu VALUES(2010, '组织查询', 2001, 1, '', '', '', 1, 0, 'F', '0', '0', 'hr:dept:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2011, '组织新增', 2001, 2, '', '', '', 1, 0, 'F', '0', '0', 'hr:dept:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2012, '组织修改', 2001, 3, '', '', '', 1, 0, 'F', '0', '0', 'hr:dept:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2013, '组织删除', 2001, 4, '', '', '', 1, 0, 'F', '0', '0', 'hr:dept:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2014, '岗位查询', 2002, 1, '', '', '', 1, 0, 'F', '0', '0', 'hr:post:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2015, '岗位新增', 2002, 2, '', '', '', 1, 0, 'F', '0', '0', 'hr:post:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2016, '岗位修改', 2002, 3, '', '', '', 1, 0, 'F', '0', '0', 'hr:post:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2017, '岗位删除', 2002, 4, '', '', '', 1, 0, 'F', '0', '0', 'hr:post:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2020, '员工查询', 2003, 1, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2021, '员工新增', 2003, 2, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2022, '员工修改', 2003, 3, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2023, '员工删除', 2003, 4, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2024, '员工导入', 2003, 5, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:import', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2025, '员工导出', 2003, 6, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:export', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2029, '敏感字段查看', 2003, 7, '', '', '', 1, 0, 'F', '0', '0', 'hr:employee:sensitive', '#', 100, 1, sysdate(), null, null, '身份证、联系方式明文');

-- Role-menu matrix: HR manages everything, managers and employees read the roster within their data scope.
INSERT INTO sys_role_menu VALUES(20, 2000);
INSERT INTO sys_role_menu VALUES(20, 2001);
INSERT INTO sys_role_menu VALUES(20, 2002);
INSERT INTO sys_role_menu VALUES(20, 2003);
INSERT INTO sys_role_menu VALUES(20, 2010);
INSERT INTO sys_role_menu VALUES(20, 2011);
INSERT INTO sys_role_menu VALUES(20, 2012);
INSERT INTO sys_role_menu VALUES(20, 2013);
INSERT INTO sys_role_menu VALUES(20, 2014);
INSERT INTO sys_role_menu VALUES(20, 2015);
INSERT INTO sys_role_menu VALUES(20, 2016);
INSERT INTO sys_role_menu VALUES(20, 2017);
INSERT INTO sys_role_menu VALUES(20, 2020);
INSERT INTO sys_role_menu VALUES(20, 2021);
INSERT INTO sys_role_menu VALUES(20, 2022);
INSERT INTO sys_role_menu VALUES(20, 2023);
INSERT INTO sys_role_menu VALUES(20, 2024);
INSERT INTO sys_role_menu VALUES(20, 2025);
INSERT INTO sys_role_menu VALUES(20, 2029);
INSERT INTO sys_role_menu VALUES(21, 2000);
INSERT INTO sys_role_menu VALUES(21, 2001);
INSERT INTO sys_role_menu VALUES(21, 2003);
INSERT INTO sys_role_menu VALUES(21, 2010);
INSERT INTO sys_role_menu VALUES(21, 2020);
INSERT INTO sys_role_menu VALUES(22, 2000);
INSERT INTO sys_role_menu VALUES(22, 2003);
INSERT INTO sys_role_menu VALUES(22, 2020);
