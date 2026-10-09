-- AgentOA module 1 P1: employee change (transfer/salary), contracts, education and work experience.
-- API contract: docs/05-api-spec.md sections 3.3/3.6. Tables follow docs/04-database-design.md section 3
-- with the V4 naming convention (oa_ prefix, snowflake ids, tenant_id + audit columns).

-- Salary is stored as ciphertext (nonce/tag included) with a key version; plaintext never lands in a numeric column.
ALTER TABLE oa_employee ADD COLUMN base_salary VARCHAR(512) DEFAULT NULL COMMENT '基本工资密文(含nonce/tag)';
ALTER TABLE oa_employee ADD COLUMN salary_key_version VARCHAR(16) DEFAULT NULL COMMENT '薪资密钥版本';

CREATE TABLE oa_employee_education (
  id            BIGINT       NOT NULL                   COMMENT '教育经历ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  employee_id   BIGINT       NOT NULL                   COMMENT '员工档案ID',
  school        VARCHAR(128) DEFAULT NULL               COMMENT '学校',
  major         VARCHAR(128) DEFAULT NULL               COMMENT '专业',
  education     VARCHAR(32)  DEFAULT NULL               COMMENT '学历',
  degree        VARCHAR(32)  DEFAULT NULL               COMMENT '学位',
  start_date    DATE         DEFAULT NULL               COMMENT '开始日期',
  end_date      DATE         DEFAULT NULL               COMMENT '结束日期',
  is_full_time  TINYINT      NOT NULL DEFAULT 1         COMMENT '是否全日制(1是 0否)',
  create_dept   BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_education_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工教育经历';

CREATE TABLE oa_employee_work (
  id            BIGINT       NOT NULL                   COMMENT '工作经历ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  employee_id   BIGINT       NOT NULL                   COMMENT '员工档案ID',
  company       VARCHAR(128) DEFAULT NULL               COMMENT '公司',
  position      VARCHAR(64)  DEFAULT NULL               COMMENT '职位',
  start_date    DATE         DEFAULT NULL               COMMENT '开始日期',
  end_date      DATE         DEFAULT NULL               COMMENT '结束日期',
  leave_reason  VARCHAR(255) DEFAULT NULL               COMMENT '离职原因',
  create_dept   BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_work_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工工作经历';

-- Employee change history: transfer / salary change / promotion / demotion, applied at effective_date.
CREATE TABLE oa_employee_change (
  id                  BIGINT       NOT NULL                   COMMENT '异动ID',
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  employee_id         BIGINT       NOT NULL                   COMMENT '员工档案ID',
  change_type         TINYINT      NOT NULL                   COMMENT '异动类型(3调岗 4调薪 5晋升 6降级)',
  effective_date      DATE         NOT NULL                   COMMENT '生效日期',
  old_dept_id         BIGINT       DEFAULT NULL               COMMENT '原部门',
  new_dept_id         BIGINT       DEFAULT NULL               COMMENT '新部门',
  old_post_id         BIGINT       DEFAULT NULL               COMMENT '原岗位',
  new_post_id         BIGINT       DEFAULT NULL               COMMENT '新岗位',
  old_position_level  VARCHAR(32)  DEFAULT NULL               COMMENT '原职级',
  new_position_level  VARCHAR(32)  DEFAULT NULL               COMMENT '新职级',
  old_salary          VARCHAR(512) DEFAULT NULL               COMMENT '原薪资密文(含nonce/tag)',
  new_salary          VARCHAR(512) DEFAULT NULL               COMMENT '新薪资密文(含nonce/tag)',
  salary_key_version  VARCHAR(16)  DEFAULT NULL               COMMENT '薪资密钥版本',
  reason              VARCHAR(255) DEFAULT NULL               COMMENT '异动原因',
  applied             TINYINT      NOT NULL DEFAULT 0         COMMENT '是否已生效(1是 0否)',
  event_id            VARCHAR(64)  DEFAULT NULL               COMMENT '生效事件ID(幂等)',
  flow_instance_id    VARCHAR(64)  DEFAULT NULL               COMMENT '关联流程实例ID',
  source_request_id   BIGINT       DEFAULT NULL               COMMENT '来源申请ID(一次生效)',
  create_dept         BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by           BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark              VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_change_source_request (source_request_id),
  KEY idx_change_employee (employee_id),
  KEY idx_change_type (change_type),
  KEY idx_change_effective (effective_date, applied)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工异动历史';

CREATE TABLE oa_contract (
  id             BIGINT       NOT NULL                   COMMENT '合同ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  employee_id    BIGINT       NOT NULL                   COMMENT '员工档案ID',
  contract_no    VARCHAR(64)  DEFAULT NULL               COMMENT '合同编号',
  contract_type  VARCHAR(32)  NOT NULL DEFAULT 'FIXED_TERM' COMMENT '合同类型(FIXED_TERM/OPEN_ENDED/TASK_BASED)',
  start_date     DATE         NOT NULL                   COMMENT '合同开始日期',
  end_date       DATE         DEFAULT NULL               COMMENT '合同结束日期',
  sign_date      DATE         DEFAULT NULL               COMMENT '签订日期',
  renew_count    INT          NOT NULL DEFAULT 0         COMMENT '续签次数',
  status         TINYINT      NOT NULL DEFAULT 1         COMMENT '状态(1生效 2到期 3终止)',
  file_id        BIGINT       DEFAULT NULL               COMMENT '合同扫描件文件ID',
  create_dept    BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_no (contract_no),
  KEY idx_contract_employee (employee_id),
  KEY idx_contract_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工合同';

-- Dictionaries (type ids continue after 112, data ids after 1033).
INSERT INTO sys_dict_type VALUES(120, '000000', '员工异动类型', 'hr_change_type', 100, 1, sysdate(), null, null, '调岗调薪等异动类型');
INSERT INTO sys_dict_data VALUES(1100, '000000', 3, '调岗', '3', 'hr_change_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1101, '000000', 4, '调薪', '4', 'hr_change_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1102, '000000', 5, '晋升', '5', 'hr_change_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1103, '000000', 6, '降级', '6', 'hr_change_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');

INSERT INTO sys_dict_type VALUES(121, '000000', '合同类型', 'hr_contract_type', 100, 1, sysdate(), null, null, '劳动合同类型');
INSERT INTO sys_dict_data VALUES(1110, '000000', 1, '固定期限', 'FIXED_TERM', 'hr_contract_type', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1111, '000000', 2, '无固定期限', 'OPEN_ENDED', 'hr_contract_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1112, '000000', 3, '以完成一定工作任务', 'TASK_BASED', 'hr_contract_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');

INSERT INTO sys_dict_type VALUES(122, '000000', '合同状态', 'hr_contract_status', 100, 1, sysdate(), null, null, '劳动合同状态');
INSERT INTO sys_dict_data VALUES(1120, '000000', 1, '生效', '1', 'hr_contract_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1121, '000000', 2, '到期', '2', 'hr_contract_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1122, '000000', 3, '终止', '3', 'hr_contract_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');

-- Menus (ids 2800+ keep clear of the 2000-2711 ranges used by previous migrations).
INSERT INTO sys_menu VALUES(2800, '员工异动', 2000, 4, 'change', 'hr/change/index', '', 1, 0, 'C', '0', '0', 'hr:change:list', 'form', 100, 1, sysdate(), null, null, '调岗调薪异动记录');
INSERT INTO sys_menu VALUES(2801, '异动查询', 2800, 1, '', '', '', 1, 0, 'F', '0', '0', 'hr:change:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2802, '异动发起', 2800, 2, '', '', '', 1, 0, 'F', '0', '0', 'hr:change:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2803, '敏感薪资查看', 2800, 3, '', '', '', 1, 0, 'F', '0', '0', 'hr:change:sensitive', '#', 100, 1, sysdate(), null, null, '调薪金额明文');
INSERT INTO sys_menu VALUES(2810, '合同管理', 2000, 5, 'contract', 'hr/contract/index', '', 1, 0, 'C', '0', '0', 'hr:contract:list', 'documentation', 100, 1, sysdate(), null, null, '劳动合同与续签提醒');
INSERT INTO sys_menu VALUES(2811, '合同查询', 2810, 1, '', '', '', 1, 0, 'F', '0', '0', 'hr:contract:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2812, '合同新增', 2810, 2, '', '', '', 1, 0, 'F', '0', '0', 'hr:contract:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2813, '合同修改', 2810, 3, '', '', '', 1, 0, 'F', '0', '0', 'hr:contract:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2814, '合同删除', 2810, 4, '', '', '', 1, 0, 'F', '0', '0', 'hr:contract:remove', '#', 100, 1, sysdate(), null, null, '');

-- Role-menu matrix: HR manages changes and contracts; managers review them; employees only see their own via self-service.
INSERT INTO sys_role_menu VALUES(20, 2800);
INSERT INTO sys_role_menu VALUES(20, 2801);
INSERT INTO sys_role_menu VALUES(20, 2802);
INSERT INTO sys_role_menu VALUES(20, 2803);
INSERT INTO sys_role_menu VALUES(20, 2810);
INSERT INTO sys_role_menu VALUES(20, 2811);
INSERT INTO sys_role_menu VALUES(20, 2812);
INSERT INTO sys_role_menu VALUES(20, 2813);
INSERT INTO sys_role_menu VALUES(20, 2814);
INSERT INTO sys_role_menu VALUES(21, 2800);
INSERT INTO sys_role_menu VALUES(21, 2801);
INSERT INTO sys_role_menu VALUES(21, 2810);
INSERT INTO sys_role_menu VALUES(21, 2811);
