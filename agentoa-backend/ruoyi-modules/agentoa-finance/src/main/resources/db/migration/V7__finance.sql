-- AgentOA module 4: finance expense types, invoices, reimburse payments and expense reports.
-- API contract: docs/05-api-spec.md section 6. Business ids are assigned snowflake values.
-- Naming follows V4: docs/04 fn_* drafts are unified to the oa_ prefix; the claim master
-- (docs/04 fn_reimburse) is reused from V5 oa_reimburse_request, so only finance-owned
-- tables are created here (docs/14 step 1).

-- ---------------------------------------------------------------- expense types

CREATE TABLE oa_expense_type (
  id             BIGINT       NOT NULL                   COMMENT '费用类型ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  parent_id      BIGINT       NOT NULL DEFAULT 0         COMMENT '父类型ID',
  name           VARCHAR(64)  NOT NULL                   COMMENT '类型名称',
  code           VARCHAR(32)  NOT NULL                   COMMENT '类型编码',
  sort           INT          NOT NULL DEFAULT 0         COMMENT '显示排序',
  budget_control TINYINT      NOT NULL DEFAULT 0         COMMENT '是否受预算控制（1是 0否）',
  status         CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept    BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_expense_type_code (code),
  KEY idx_expense_type_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用类型';

-- ---------------------------------------------------------------- invoice identity and occupation

CREATE TABLE oa_invoice (
  id                   BIGINT        NOT NULL                   COMMENT '发票ID',
  tenant_id            VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  invoice_type         VARCHAR(32)   NOT NULL                   COMMENT '发票类型',
  invoice_code         VARCHAR(32)   NOT NULL DEFAULT ''        COMMENT '发票代码（无代码为空串）',
  invoice_no           VARCHAR(64)   NOT NULL                   COMMENT '发票号码',
  invoice_date         DATE          DEFAULT NULL               COMMENT '开票日期',
  amount               DECIMAL(12,2) NOT NULL                   COMMENT '发票金额',
  fingerprint          CHAR(64)      NOT NULL                   COMMENT '规范化身份指纹（SHA-256）',
  file_id              BIGINT        DEFAULT NULL               COMMENT '发票图片文件ID（sys_file）',
  owner_user_id        BIGINT        NOT NULL                   COMMENT '发票归属账号ID',
  occupied_reimburse_id BIGINT       DEFAULT NULL               COMMENT '在途占用报销单ID',
  paid_reimburse_id    BIGINT        DEFAULT NULL               COMMENT '已付款报销单ID（永久占用）',
  lock_version         INT           NOT NULL DEFAULT 0         COMMENT '乐观锁',
  create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time          DATETIME      DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice_identity (invoice_type, invoice_code, invoice_no),
  KEY idx_invoice_owner (owner_user_id),
  KEY idx_invoice_occupation (occupied_reimburse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票与当前报销占用';

CREATE TABLE oa_invoice_reservation (
  id            BIGINT       NOT NULL                   COMMENT '占用流水ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  invoice_id    BIGINT       NOT NULL                   COMMENT '发票ID',
  reimburse_id  BIGINT       NOT NULL                   COMMENT '报销单ID',
  action        VARCHAR(16)  NOT NULL                   COMMENT '动作（OCCUPY/RELEASE/PAID）',
  event_key     VARCHAR(128) NOT NULL                   COMMENT '业务事件ID（幂等去重）',
  operator_id   BIGINT       NOT NULL                   COMMENT '操作账号ID',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_event (event_key),
  KEY idx_reservation_invoice (invoice_id),
  KEY idx_reservation_reimburse (reimburse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票占用流水（提交占用、拒绝/撤销释放、付款转永久）';

-- ---------------------------------------------------------------- claim financial items (snapshot at submit)

CREATE TABLE oa_expense_item (
  id             BIGINT        NOT NULL                   COMMENT '明细ID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  reimburse_id   BIGINT        NOT NULL                   COMMENT '报销单ID（oa_reimburse_request）',
  submission_no  INT           NOT NULL DEFAULT 0         COMMENT '提交序号',
  invoice_id     BIGINT        DEFAULT NULL               COMMENT '关联发票ID',
  expense_type_id BIGINT       DEFAULT NULL               COMMENT '费用类型ID',
  expense_type   VARCHAR(32)   DEFAULT NULL               COMMENT '费用类型编码快照',
  occur_date     DATE          DEFAULT NULL               COMMENT '发生日期',
  amount         DECIMAL(12,2) NOT NULL                   COMMENT '金额',
  description    VARCHAR(255)  DEFAULT NULL               COMMENT '说明',
  sort           INT           NOT NULL DEFAULT 0         COMMENT '显示排序',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_item_reimburse_invoice (reimburse_id, invoice_id),
  KEY idx_item_reimburse (reimburse_id),
  KEY idx_item_type (expense_type_id),
  KEY idx_item_occur_date (occur_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销明细（提交时固化的财务口径）';

-- ---------------------------------------------------------------- payment registration

CREATE TABLE oa_payment_record (
  id             BIGINT        NOT NULL                   COMMENT '付款记录ID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  payment_no     VARCHAR(32)   NOT NULL                   COMMENT '付款单号',
  reimburse_id   BIGINT        NOT NULL                   COMMENT '报销单ID',
  amount         DECIMAL(12,2) NOT NULL                   COMMENT '付款金额',
  pay_date       DATE          DEFAULT NULL               COMMENT '付款日期',
  payment_method VARCHAR(32)   DEFAULT NULL               COMMENT '付款方式（BANK_TRANSFER/CASH/OTHER）',
  voucher_no     VARCHAR(64)   DEFAULT NULL               COMMENT '凭证号',
  pay_status     TINYINT       NOT NULL DEFAULT 2         COMMENT '状态（1待付 2已付 3失败）',
  operator_id    BIGINT        NOT NULL                   COMMENT '登记出纳账号ID',
  create_dept    BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_payment_reimburse (reimburse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款登记（一次全额付款）';

-- ---------------------------------------------------------------- finance ledger events

CREATE TABLE oa_finance_event (
  id            BIGINT        NOT NULL                   COMMENT '事件ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  event_key     VARCHAR(128)  NOT NULL                   COMMENT '业务事件ID（幂等去重）',
  biz_type      VARCHAR(32)   NOT NULL                   COMMENT '业务类型（reimburse/invoice/payment）',
  biz_id        BIGINT        NOT NULL                   COMMENT '业务ID',
  action        VARCHAR(32)   NOT NULL                   COMMENT '动作（OCCUPY/RELEASE/APPROVE/PAY）',
  amount        DECIMAL(12,2) DEFAULT NULL               COMMENT '涉及金额',
  operator_id   BIGINT        NOT NULL                   COMMENT '操作账号ID',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '说明',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_finance_event (event_key),
  KEY idx_finance_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务事件流水（占用/释放/审批/付款）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(60, '000000', '发票类型', 'fn_invoice_type', 100, 1, sysdate(), null, null, '发票类型');
INSERT INTO sys_dict_data VALUES(600, '000000', 1, '增值税专用发票', 'VAT_SPECIAL', 'fn_invoice_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(601, '000000', 2, '增值税普通发票', 'VAT_NORMAL', 'fn_invoice_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(602, '000000', 3, '电子发票', 'ELECTRONIC', 'fn_invoice_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(603, '000000', 4, '其他', 'OTHER', 'fn_invoice_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(61, '000000', '付款方式', 'fn_payment_method', 100, 1, sysdate(), null, null, '报销与付款方式');
INSERT INTO sys_dict_data VALUES(610, '000000', 1, '银行转账', 'BANK_TRANSFER', 'fn_payment_method', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(611, '000000', 2, '现金', 'CASH', 'fn_payment_method', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(612, '000000', 3, '其他', 'OTHER', 'fn_payment_method', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(62, '000000', '付款状态', 'fn_pay_status', 100, 1, sysdate(), null, null, '付款登记状态');
INSERT INTO sys_dict_data VALUES(620, '000000', 1, '待付款', '1', 'fn_pay_status', '', 'warning', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(621, '000000', 2, '已付款', '2', 'fn_pay_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(622, '000000', 3, '付款失败', '3', 'fn_pay_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(63, '000000', '财务事件动作', 'fn_finance_action', 100, 1, sysdate(), null, null, '财务流水动作');
INSERT INTO sys_dict_data VALUES(630, '000000', 1, '发票占用', 'OCCUPY', 'fn_finance_action', '', 'warning', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(631, '000000', 2, '释放占用', 'RELEASE', 'fn_finance_action', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(632, '000000', 3, '审批通过', 'APPROVE', 'fn_finance_action', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(633, '000000', 4, '登记付款', 'PAY', 'fn_finance_action', '', 'success', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- seed expense types (docs/04 12.1)

INSERT INTO oa_expense_type VALUES(1, '000000', 0, '差旅费', 'travel', 1, 0, '0', null, null, sysdate(), null, null, '交通、住宿、出差补贴');
INSERT INTO oa_expense_type VALUES(2, '000000', 0, '办公费', 'office', 2, 0, '0', null, null, sysdate(), null, null, '办公用品与耗材');
INSERT INTO oa_expense_type VALUES(3, '000000', 0, '餐费', 'meal', 3, 0, '0', null, null, sysdate(), null, null, '业务招待与加班餐');
INSERT INTO oa_expense_type VALUES(4, '000000', 0, '交通费', 'transport', 4, 0, '0', null, null, sysdate(), null, null, '市内交通');
INSERT INTO oa_expense_type VALUES(5, '000000', 0, '其他', 'other', 99, 0, '0', null, null, sysdate(), null, null, '其他费用');

-- ---------------------------------------------------------------- menus and buttons (permission strings follow docs/05 section 6 scope rules)

INSERT INTO sys_menu VALUES(2300, '报销财务', 0, 9, 'finance', null, '', 1, 0, 'M', '0', '0', '', 'money', 100, 1, sysdate(), null, null, '报销财务目录');
INSERT INTO sys_menu VALUES(2301, '我的报销', 2300, 1, 'reimburse', 'finance/reimburse/index', '', 1, 0, 'C', '0', '0', '', 'form', 100, 1, sysdate(), null, null, '报销单创建、提交与进度');
INSERT INTO sys_menu VALUES(2302, '发票管理', 2300, 2, 'invoice', 'finance/invoice/index', '', 1, 0, 'C', '0', '0', '', 'clipboard', 100, 1, sysdate(), null, null, '发票录入与占用状态');
INSERT INTO sys_menu VALUES(2303, '费用类型', 2300, 3, 'expense-type', 'finance/expense-type/index', '', 1, 0, 'C', '0', '0', 'fn:expense-type:list', 'tree-table', 100, 1, sysdate(), null, null, '费用类型维护');
INSERT INTO sys_menu VALUES(2304, '付款登记', 2300, 4, 'payment', 'finance/payment/index', '', 1, 0, 'C', '0', '0', 'fn:payment:list', 'list', 100, 1, sysdate(), null, null, '待付款单与人工付款登记');
INSERT INTO sys_menu VALUES(2305, '费用报表', 2300, 5, 'report', 'finance/report/index', '', 1, 0, 'C', '0', '0', 'fn:report:list', 'chart', 100, 1, sysdate(), null, null, '部门/费用类型统计与导出');
INSERT INTO sys_menu VALUES(2310, '发票详情查询', 2302, 1, '', '', '', 1, 0, 'F', '0', '0', 'fn:invoice:query', '#', 100, 1, sysdate(), null, null, '查看授权范围内发票明文与图片');
INSERT INTO sys_menu VALUES(2311, '费用类型查询', 2303, 1, '', '', '', 1, 0, 'F', '0', '0', 'fn:expense-type:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2312, '费用类型新增', 2303, 2, '', '', '', 1, 0, 'F', '0', '0', 'fn:expense-type:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2313, '费用类型修改', 2303, 3, '', '', '', 1, 0, 'F', '0', '0', 'fn:expense-type:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2314, '费用类型删除', 2303, 4, '', '', '', 1, 0, 'F', '0', '0', 'fn:expense-type:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2315, '付款查询', 2304, 1, '', '', '', 1, 0, 'F', '0', '0', 'fn:payment:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2316, '付款登记', 2304, 2, '', '', '', 1, 0, 'F', '0', '0', 'fn:payment:add', '#', 100, 1, sysdate(), null, null, '登记人工全额付款');
INSERT INTO sys_menu VALUES(2317, '费用报表导出', 2305, 1, '', '', '', 1, 0, 'F', '0', '0', 'fn:report:export', '#', 100, 1, sysdate(), null, null, '');

-- Role-menu matrix: employees file claims and invoices, finance maintains types and registers
-- payments, cashier only registers payments, managers read department reports.
INSERT INTO sys_role_menu VALUES(20, 2300);
INSERT INTO sys_role_menu VALUES(20, 2301);
INSERT INTO sys_role_menu VALUES(20, 2302);
INSERT INTO sys_role_menu VALUES(21, 2300);
INSERT INTO sys_role_menu VALUES(21, 2301);
INSERT INTO sys_role_menu VALUES(21, 2302);
INSERT INTO sys_role_menu VALUES(21, 2305);
INSERT INTO sys_role_menu VALUES(21, 2317);
INSERT INTO sys_role_menu VALUES(22, 2300);
INSERT INTO sys_role_menu VALUES(22, 2301);
INSERT INTO sys_role_menu VALUES(22, 2302);
INSERT INTO sys_role_menu VALUES(23, 2300);
INSERT INTO sys_role_menu VALUES(23, 2301);
INSERT INTO sys_role_menu VALUES(23, 2302);
INSERT INTO sys_role_menu VALUES(23, 2303);
INSERT INTO sys_role_menu VALUES(23, 2304);
INSERT INTO sys_role_menu VALUES(23, 2305);
INSERT INTO sys_role_menu VALUES(23, 2310);
INSERT INTO sys_role_menu VALUES(23, 2311);
INSERT INTO sys_role_menu VALUES(23, 2312);
INSERT INTO sys_role_menu VALUES(23, 2313);
INSERT INTO sys_role_menu VALUES(23, 2314);
INSERT INTO sys_role_menu VALUES(23, 2315);
INSERT INTO sys_role_menu VALUES(23, 2316);
INSERT INTO sys_role_menu VALUES(23, 2317);
INSERT INTO sys_role_menu VALUES(24, 2300);
INSERT INTO sys_role_menu VALUES(24, 2301);
INSERT INTO sys_role_menu VALUES(24, 2302);
INSERT INTO sys_role_menu VALUES(24, 2304);
INSERT INTO sys_role_menu VALUES(24, 2315);
INSERT INTO sys_role_menu VALUES(24, 2316);
INSERT INTO sys_role_menu VALUES(25, 2300);
INSERT INTO sys_role_menu VALUES(25, 2301);
INSERT INTO sys_role_menu VALUES(25, 2302);
