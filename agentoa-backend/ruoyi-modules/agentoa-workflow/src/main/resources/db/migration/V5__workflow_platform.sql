-- AgentOA module 2: controlled workflow platform (Flowable) and the six P0 business requests.
-- API contract: docs/05-api-spec.md section 4. Delivery plan: docs/12-module-plan-workflow.md.
-- Flow template rows (oa_flow_definition / oa_flow_definition_version / oa_flow_form_version)
-- are registered from the code repository by TemplateRegistrar at startup, not seeded here,
-- so validation digests stay real. Categories, dictionaries, roles and menus are seeded here.

-- ------------------------------------------------------------------ flow platform

CREATE TABLE oa_flow_category (
  id          BIGINT       NOT NULL                   COMMENT '分类ID',
  tenant_id   VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  code        VARCHAR(32)  NOT NULL                   COMMENT '分类编码',
  name        VARCHAR(64)  NOT NULL                   COMMENT '分类名称',
  sort        INT          NOT NULL DEFAULT 0         COMMENT '显示顺序',
  status      CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by   BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark      VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程分类';

CREATE TABLE oa_flow_definition (
  id                 BIGINT       NOT NULL                   COMMENT '流程定义ID',
  tenant_id          VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  process_key        VARCHAR(64)  NOT NULL                   COMMENT '流程Key',
  process_name       VARCHAR(64)  NOT NULL                   COMMENT '流程名称',
  category_id        BIGINT       NOT NULL                   COMMENT '分类ID',
  form_key           VARCHAR(64)  NOT NULL                   COMMENT '表单Key',
  business_type      VARCHAR(32)  NOT NULL                   COMMENT '业务类型 leave/overtime/correction/reimburse/regularize/offboard',
  current_version_no INT          NOT NULL DEFAULT 1         COMMENT '当前发布版本号',
  status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'   COMMENT '状态 DRAFT/PUBLISHED/RETIRED',
  icon               VARCHAR(64)  DEFAULT NULL               COMMENT '图标',
  sort               INT          NOT NULL DEFAULT 0         COMMENT '显示顺序',
  create_dept        BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by          BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time        DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_def_key (process_key),
  KEY idx_flow_def_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义';

CREATE TABLE oa_flow_definition_version (
  id                     BIGINT       NOT NULL                   COMMENT '定义版本ID',
  tenant_id              VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  definition_id          BIGINT       NOT NULL                   COMMENT '流程定义ID',
  version_no             INT          NOT NULL                   COMMENT '版本号（不可变）',
  bpmn_resource          VARCHAR(255) NOT NULL                   COMMENT 'BPMN classpath 资源路径',
  bpmn_digest            CHAR(64)     NOT NULL                   COMMENT 'BPMN SHA-256 校验摘要',
  validation_summary     VARCHAR(500) DEFAULT NULL               COMMENT '校验摘要（受控元素清单）',
  status                 VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'   COMMENT '状态 DRAFT/PUBLISHED/RETIRED',
  flowable_proc_def_id   VARCHAR(64)  DEFAULT NULL               COMMENT 'Flowable 流程定义ID',
  flowable_deployment_id VARCHAR(64)  DEFAULT NULL               COMMENT 'Flowable 部署ID',
  published_by           BIGINT        DEFAULT NULL              COMMENT '发布人账号ID',
  published_time         DATETIME      DEFAULT NULL              COMMENT '发布时间',
  create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time            DATETIME      DEFAULT NULL              COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_def_version (definition_id, version_no),
  KEY idx_flow_version_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义版本（发布后不可变）';

CREATE TABLE oa_flow_form_version (
  id            BIGINT       NOT NULL                   COMMENT '表单版本ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  form_key      VARCHAR(64)  NOT NULL                   COMMENT '表单Key',
  form_name     VARCHAR(64)  NOT NULL                   COMMENT '表单名称',
  version_no    INT          NOT NULL                   COMMENT '版本号（不可变）',
  schema_json   JSON         NOT NULL                   COMMENT '表单 JSON Schema（schemaVersion+fields）',
  schema_digest CHAR(64)     NOT NULL                   COMMENT 'Schema SHA-256 校验摘要',
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'   COMMENT '状态 DRAFT/PUBLISHED/RETIRED',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME      DEFAULT NULL              COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_form_version (form_key, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单版本';

CREATE TABLE oa_flow_instance (
  id                    BIGINT       NOT NULL                   COMMENT '流程实例ID',
  tenant_id             VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  definition_id         BIGINT       NOT NULL                   COMMENT '流程定义ID（固定）',
  definition_version_id BIGINT       NOT NULL                   COMMENT '定义版本ID（固定，新版本不影响）',
  form_version_id       BIGINT       NOT NULL                   COMMENT '表单版本ID（固定）',
  form_schema_snapshot  JSON         NOT NULL                   COMMENT '提交时表单结构快照',
  form_data             JSON         DEFAULT NULL               COMMENT '表单数据',
  business_type         VARCHAR(32)  NOT NULL                   COMMENT '业务类型',
  business_id           BIGINT       NOT NULL                   COMMENT '业务单据ID',
  business_key          VARCHAR(64)  DEFAULT NULL               COMMENT '业务单号',
  submission_no         INT          NOT NULL DEFAULT 1         COMMENT '同一业务单据提交序号',
  title                 VARCHAR(255) DEFAULT NULL               COMMENT '流程标题',
  initiator_user_id     BIGINT       NOT NULL                   COMMENT '发起人账号ID',
  initiator_name        VARCHAR(64)  DEFAULT NULL               COMMENT '发起人姓名',
  initiator_dept_id     BIGINT       DEFAULT NULL               COMMENT '发起人部门ID',
  priority              TINYINT      NOT NULL DEFAULT 0         COMMENT '优先级（0普通 1紧急 2特急）',
  status                TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1审批中 2已通过 3已拒绝 4已撤销 5已挂起 6已终止）',
  flowable_proc_inst_id VARCHAR(64)  DEFAULT NULL               COMMENT 'Flowable 流程实例ID',
  current_task_name     VARCHAR(64)  DEFAULT NULL               COMMENT '当前节点名称',
  current_assignees     VARCHAR(255) DEFAULT NULL               COMMENT '当前办理人（逗号分隔，展示快照）',
  start_time            DATETIME     DEFAULT NULL               COMMENT '发起时间',
  end_time              DATETIME     DEFAULT NULL               COMMENT '结束时间',
  duration              BIGINT       DEFAULT NULL               COMMENT '耗时（毫秒）',
  lock_version          INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept           BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by             BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by             BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time           DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark                VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_inst_proc (flowable_proc_inst_id),
  UNIQUE KEY uk_flow_inst_biz (business_type, business_id, submission_no),
  KEY idx_flow_inst_initiator (initiator_user_id),
  KEY idx_flow_inst_status (status),
  KEY idx_flow_inst_start (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例';

CREATE TABLE oa_flow_task_action (
  id                BIGINT       NOT NULL                   COMMENT '操作ID',
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  instance_id       BIGINT       NOT NULL                   COMMENT '流程实例ID',
  flowable_task_id  VARCHAR(64)  DEFAULT NULL               COMMENT 'Flowable 任务ID',
  task_name         VARCHAR(64)  DEFAULT NULL               COMMENT '任务名称',
  task_def_key      VARCHAR(64)  DEFAULT NULL               COMMENT '任务定义Key',
  action            VARCHAR(16)  NOT NULL                   COMMENT '动作 agree/reject/transfer/cancel',
  operator_user_id  BIGINT       NOT NULL                   COMMENT '操作人账号ID',
  operator_name     VARCHAR(64)  DEFAULT NULL               COMMENT '操作人姓名',
  old_assignee_id   BIGINT       DEFAULT NULL               COMMENT '原办理人账号ID',
  old_assignee_name VARCHAR(64)  DEFAULT NULL               COMMENT '原办理人姓名',
  new_assignee_id   BIGINT       DEFAULT NULL               COMMENT '新办理人账号ID',
  new_assignee_name VARCHAR(64)  DEFAULT NULL               COMMENT '新办理人姓名',
  comment           VARCHAR(1000) DEFAULT NULL              COMMENT '审批意见',
  action_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_flow_action_instance (instance_id, action_time),
  KEY idx_flow_action_operator (operator_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程任务操作历史';

CREATE TABLE oa_flow_business_ref (
  id            BIGINT       NOT NULL                   COMMENT '关联ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  business_type VARCHAR(32)  NOT NULL                   COMMENT '业务类型',
  business_id   BIGINT       NOT NULL                   COMMENT '业务单据ID',
  instance_id   BIGINT       NOT NULL                   COMMENT '流程实例ID',
  submission_no INT          NOT NULL DEFAULT 1         COMMENT '提交序号',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_biz_ref (business_type, business_id, submission_no),
  KEY idx_flow_biz_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程业务关联';

-- Idempotency-Key records for workflow writes (scope: user + key, retained 24h, API spec 1.7).
CREATE TABLE oa_flow_idempotency (
  id           BIGINT       NOT NULL                   COMMENT '记录ID',
  idem_key     VARCHAR(64)  NOT NULL                   COMMENT 'Idempotency-Key',
  user_id      BIGINT       NOT NULL                   COMMENT '调用方账号ID',
  request_path VARCHAR(255) NOT NULL                   COMMENT '请求路径',
  body_digest  CHAR(64)     NOT NULL                   COMMENT '请求体摘要',
  result_ref   VARCHAR(64)  DEFAULT NULL               COMMENT '首次结果引用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_idem_user_key (user_id, idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程写接口幂等记录';

-- ------------------------------------------------------------------ six P0 business requests (minimal carriers)

CREATE TABLE oa_leave_request (
  id               BIGINT       NOT NULL                   COMMENT '请假单ID',
  tenant_id        VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id          BIGINT       NOT NULL                   COMMENT '申请人账号ID',
  employee_id      BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  leave_type       VARCHAR(32)  NOT NULL                   COMMENT '请假类型',
  start_time       DATETIME     NOT NULL                   COMMENT '开始时间',
  end_time         DATETIME     NOT NULL                   COMMENT '结束时间',
  duration_minutes INT          NOT NULL DEFAULT 0         COMMENT '服务端计算时长（分钟）',
  reason           VARCHAR(500) DEFAULT NULL               COMMENT '事由',
  status           TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 7已撤销）',
  flow_instance_id BIGINT       DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT          NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_leave_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请（最小承接单，额度账本见考勤模块）';

CREATE TABLE oa_overtime_request (
  id               BIGINT       NOT NULL                   COMMENT '加班单ID',
  tenant_id        VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id          BIGINT       NOT NULL                   COMMENT '申请人账号ID',
  employee_id      BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  overtime_date    DATE         NOT NULL                   COMMENT '加班日期',
  overtime_type    VARCHAR(32)  NOT NULL DEFAULT 'weekday' COMMENT '加班类型',
  start_time       DATETIME     NOT NULL                   COMMENT '开始时间',
  end_time         DATETIME     NOT NULL                   COMMENT '结束时间',
  duration_minutes INT          NOT NULL DEFAULT 0         COMMENT '服务端计算时长（分钟）',
  reason           VARCHAR(255) DEFAULT NULL               COMMENT '事由',
  status           TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 7已撤销）',
  flow_instance_id BIGINT       DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT          NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_overtime_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加班申请（最小承接单）';

CREATE TABLE oa_correction_request (
  id               BIGINT       NOT NULL                   COMMENT '补卡单ID',
  tenant_id        VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id          BIGINT       NOT NULL                   COMMENT '申请人账号ID',
  employee_id      BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  attendance_date  DATE         NOT NULL                   COMMENT '补卡日期',
  punch_type       TINYINT      NOT NULL                   COMMENT '时段（1上班 2下班）',
  corrected_time   DATETIME     DEFAULT NULL               COMMENT '补卡时间',
  reason           VARCHAR(500) NOT NULL                   COMMENT '原因',
  status           TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 7已撤销）',
  flow_instance_id BIGINT       DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT          NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_correction_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补卡申请（最小承接单）';

CREATE TABLE oa_reimburse_request (
  id               BIGINT        NOT NULL                   COMMENT '报销单ID',
  tenant_id        VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  reimburse_no     VARCHAR(32)   NOT NULL                   COMMENT '报销单号',
  user_id          BIGINT        NOT NULL                   COMMENT '申请人账号ID',
  employee_id      BIGINT        DEFAULT NULL               COMMENT '员工档案ID',
  reimburse_type   VARCHAR(32)   NOT NULL DEFAULT 'expense' COMMENT '报销类型',
  total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00      COMMENT '报销总额（服务端按明细汇总）',
  currency         VARCHAR(8)    NOT NULL DEFAULT 'CNY'     COMMENT '币种',
  pay_method       VARCHAR(32)   DEFAULT NULL               COMMENT '支付方式',
  details_json     JSON          DEFAULT NULL               COMMENT '明细（费用类型/发生日期/金额/发票号/说明）',
  reason           VARCHAR(500)  DEFAULT NULL               COMMENT '事由',
  status           TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 5待付款 6已付款 7已撤销）',
  flow_instance_id BIGINT        DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT           NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT           NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT         DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT         DEFAULT NULL              COMMENT '创建者',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT         DEFAULT NULL              COMMENT '更新者',
  update_time      DATETIME       DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reimburse_no (reimburse_no),
  KEY idx_reimburse_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销申请（最小承接单，发票占用与付款见财务模块）';

CREATE TABLE oa_lifecycle_request (
  id               BIGINT       NOT NULL                   COMMENT '生命周期申请ID',
  tenant_id        VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  employee_id      BIGINT       NOT NULL                   COMMENT '员工档案ID',
  user_id          BIGINT       DEFAULT NULL               COMMENT '申请人账号ID',
  request_type     VARCHAR(16)  NOT NULL                   COMMENT '申请类型 REGULARIZE/OFFBOARD',
  effective_date   DATE         NOT NULL                   COMMENT '生效日期',
  form_data        JSON         DEFAULT NULL               COMMENT '表单数据（评价/薪资/离职原因/交接）',
  status           TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 7已撤销）',
  flow_instance_id BIGINT       DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT          NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL              COMMENT '创建者',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL              COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_lifecycle_employee (employee_id, request_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转正与离职申请（docs/04 hr_lifecycle_request 承接单）';

-- ------------------------------------------------------------------ seed data

-- Flow categories (docs/04 section 12.1).
INSERT INTO oa_flow_category VALUES(100, '000000', 'HR', '人事流程', 1, '0', 100, 1, sysdate(), null, null, '转正、离职等人事审批');
INSERT INTO oa_flow_category VALUES(101, '000000', 'FINANCE', '财务流程', 2, '0', 100, 1, sysdate(), null, null, '报销与付款审批');
INSERT INTO oa_flow_category VALUES(102, '000000', 'ATTENDANCE', '考勤流程', 3, '0', 100, 1, sysdate(), null, null, '请假、加班、补卡审批');
INSERT INTO oa_flow_category VALUES(103, '000000', 'OTHER', '其他', 4, '0', 100, 1, sysdate(), null, null, '');

-- Dictionaries.
INSERT INTO sys_dict_type VALUES(40, '000000', '流程实例状态', 'wf_instance_status', 100, 1, sysdate(), null, null, '流程实例状态');
INSERT INTO sys_dict_data VALUES(400, '000000', 1, '审批中', '1', 'wf_instance_status', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(401, '000000', 2, '已通过', '2', 'wf_instance_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(402, '000000', 3, '已拒绝', '3', 'wf_instance_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(403, '000000', 4, '已撤销', '4', 'wf_instance_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(404, '000000', 5, '已挂起', '5', 'wf_instance_status', '', 'info', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(405, '000000', 6, '已终止', '6', 'wf_instance_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(41, '000000', '流程任务动作', 'wf_task_action', 100, 1, sysdate(), null, null, '流程任务操作动作');
INSERT INTO sys_dict_data VALUES(410, '000000', 1, '同意', 'agree', 'wf_task_action', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(411, '000000', 2, '拒绝', 'reject', 'wf_task_action', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(412, '000000', 3, '转办', 'transfer', 'wf_task_action', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(413, '000000', 4, '撤销', 'cancel', 'wf_task_action', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(42, '000000', '请假类型', 'at_leave_type', 100, 1, sysdate(), null, null, '请假额度类型');
INSERT INTO sys_dict_data VALUES(420, '000000', 1, '年假', 'annual', 'at_leave_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(421, '000000', 2, '调休', 'compensatory', 'at_leave_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(422, '000000', 3, '病假', 'sick', 'at_leave_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(423, '000000', 4, '事假', 'personal', 'at_leave_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(424, '000000', 5, '婚假', 'marriage', 'at_leave_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(425, '000000', 6, '产假', 'maternity', 'at_leave_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(426, '000000', 7, '陪产假', 'paternity', 'at_leave_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(427, '000000', 8, '丧假', 'bereavement', 'at_leave_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(43, '000000', '加班类型', 'at_overtime_type', 100, 1, sysdate(), null, null, '加班类型');
INSERT INTO sys_dict_data VALUES(430, '000000', 1, '工作日', 'weekday', 'at_overtime_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(431, '000000', 2, '周末', 'weekend', 'at_overtime_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(432, '000000', 3, '节假日', 'holiday', 'at_overtime_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(44, '000000', '报销单状态', 'fn_reimburse_status', 100, 1, sysdate(), null, null, '报销单状态');
INSERT INTO sys_dict_data VALUES(440, '000000', 1, '草稿', '1', 'fn_reimburse_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(441, '000000', 2, '审批中', '2', 'fn_reimburse_status', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(442, '000000', 3, '已通过', '3', 'fn_reimburse_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(443, '000000', 4, '已拒绝', '4', 'fn_reimburse_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(444, '000000', 5, '待付款', '5', 'fn_reimburse_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(445, '000000', 6, '已付款', '6', 'fn_reimburse_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(446, '000000', 7, '已撤销', '7', 'fn_reimburse_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(45, '000000', '申请单状态', 'wf_request_status', 100, 1, sysdate(), null, null, '业务申请单状态');
INSERT INTO sys_dict_data VALUES(450, '000000', 1, '草稿', '1', 'wf_request_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(451, '000000', 2, '审批中', '2', 'wf_request_status', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(452, '000000', 3, '已通过', '3', 'wf_request_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(453, '000000', 4, '已拒绝', '4', 'wf_request_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(454, '000000', 7, '已撤销', '7', 'wf_request_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');

-- Roles for the reimbursement approval chain and approver resolution (docs/02 section 3.2).
INSERT INTO sys_role VALUES(23, '000000', '财务', 'finance', 5, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '报销财务审核');
INSERT INTO sys_role VALUES(24, '000000', '出纳', 'cashier', 6, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '报销付款登记');
INSERT INTO sys_role VALUES(25, '000000', '总监', 'director', 7, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '大额报销审批');

-- Workflow menus and buttons (permission strings follow docs/05-api-spec.md section 4).
INSERT INTO sys_menu VALUES(2100, '流程审批', 0, 7, 'workflow', null, '', 1, 0, 'M', '0', '0', '', 'guide', 100, 1, sysdate(), null, null, '流程审批目录');
INSERT INTO sys_menu VALUES(2101, '我的待办', 2100, 1, 'todo', 'workflow/todo/index', '', 1, 0, 'C', '0', '0', '', 'form', 100, 1, sysdate(), null, null, '待我审批的流程');
INSERT INTO sys_menu VALUES(2102, '我的已办', 2100, 2, 'done', 'workflow/done/index', '', 1, 0, 'C', '0', '0', '', 'checkbox', 100, 1, sysdate(), null, null, '我已处理的流程');
INSERT INTO sys_menu VALUES(2103, '我发起的', 2100, 3, 'mine', 'workflow/mine/index', '', 1, 0, 'C', '0', '0', '', 'send', 100, 1, sysdate(), null, null, '我发起的流程');
INSERT INTO sys_menu VALUES(2104, '流程详情', 2100, 4, 'detail', 'workflow/detail/index', '', 1, 0, 'C', '1', '0', '', '#', 100, 1, sysdate(), null, null, '流程详情页（隐藏路由）');
INSERT INTO sys_menu VALUES(2105, '流程定义', 2100, 5, 'definition', 'workflow/definition/index', '', 1, 0, 'C', '0', '0', 'wf:definition:list', 'tree-table', 100, 1, sysdate(), null, null, '固定模板与版本管理');
INSERT INTO sys_menu VALUES(2110, '流程查询', 2105, 1, '', '', '', 1, 0, 'F', '0', '0', 'wf:definition:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2111, '流程编辑', 2105, 2, '', '', '', 1, 0, 'F', '0', '0', 'wf:definition:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2112, '流程发布', 2105, 3, '', '', '', 1, 0, 'F', '0', '0', 'wf:definition:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2113, '分类新增', 2105, 4, '', '', '', 1, 0, 'F', '0', '0', 'wf:category:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2114, '分类修改', 2105, 5, '', '', '', 1, 0, 'F', '0', '0', 'wf:category:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2115, '分类删除', 2105, 6, '', '', '', 1, 0, 'F', '0', '0', 'wf:category:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2116, '流程监控', 2105, 7, '', '', '', 1, 0, 'F', '0', '0', 'wf:instance:list', '#', 100, 1, sysdate(), null, null, '全部流程实例可见范围');

-- Role-menu matrix: approvers and applicants use todo/done/mine; HR reads definitions; admin holds all.
INSERT INTO sys_role_menu VALUES(20, 2100);
INSERT INTO sys_role_menu VALUES(20, 2101);
INSERT INTO sys_role_menu VALUES(20, 2102);
INSERT INTO sys_role_menu VALUES(20, 2103);
INSERT INTO sys_role_menu VALUES(20, 2104);
INSERT INTO sys_role_menu VALUES(20, 2105);
INSERT INTO sys_role_menu VALUES(20, 2110);
INSERT INTO sys_role_menu VALUES(21, 2100);
INSERT INTO sys_role_menu VALUES(21, 2101);
INSERT INTO sys_role_menu VALUES(21, 2102);
INSERT INTO sys_role_menu VALUES(21, 2103);
INSERT INTO sys_role_menu VALUES(21, 2104);
INSERT INTO sys_role_menu VALUES(22, 2100);
INSERT INTO sys_role_menu VALUES(22, 2101);
INSERT INTO sys_role_menu VALUES(22, 2102);
INSERT INTO sys_role_menu VALUES(22, 2103);
INSERT INTO sys_role_menu VALUES(22, 2104);
INSERT INTO sys_role_menu VALUES(23, 2100);
INSERT INTO sys_role_menu VALUES(23, 2101);
INSERT INTO sys_role_menu VALUES(23, 2102);
INSERT INTO sys_role_menu VALUES(23, 2103);
INSERT INTO sys_role_menu VALUES(23, 2104);
INSERT INTO sys_role_menu VALUES(24, 2100);
INSERT INTO sys_role_menu VALUES(24, 2101);
INSERT INTO sys_role_menu VALUES(24, 2102);
INSERT INTO sys_role_menu VALUES(24, 2103);
INSERT INTO sys_role_menu VALUES(24, 2104);
INSERT INTO sys_role_menu VALUES(25, 2100);
INSERT INTO sys_role_menu VALUES(25, 2101);
INSERT INTO sys_role_menu VALUES(25, 2102);
INSERT INTO sys_role_menu VALUES(25, 2103);
INSERT INTO sys_role_menu VALUES(25, 2104);
