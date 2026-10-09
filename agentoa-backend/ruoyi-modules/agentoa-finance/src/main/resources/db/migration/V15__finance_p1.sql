-- AgentOA finance P1: department/project budgets and the budget ledger (FN-03).
-- API contract: docs/05-api-spec.md section 6.3. Amounts use DECIMAL(14,2) like the rest of the module.

CREATE TABLE oa_budget (
  id                  BIGINT        NOT NULL                   COMMENT '预算ID',
  tenant_id           VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  budget_code         VARCHAR(32)   NOT NULL                   COMMENT '预算编号',
  budget_name         VARCHAR(64)   DEFAULT NULL               COMMENT '预算名称',
  budget_type         TINYINT       NOT NULL                   COMMENT '类型(1部门 2项目)',
  owner_id            BIGINT        NOT NULL                   COMMENT '归属ID(部门ID/项目ID)',
  year                INT           NOT NULL                   COMMENT '年度',
  quarter             INT           DEFAULT NULL               COMMENT '季度(1-4,空=年度)',
  total_amount        DECIMAL(14,2) NOT NULL                   COMMENT '预算总额',
  used_amount         DECIMAL(14,2) NOT NULL DEFAULT 0         COMMENT '已用金额',
  frozen_amount       DECIMAL(14,2) NOT NULL DEFAULT 0         COMMENT '冻结金额',
  warn_threshold      INT           NOT NULL DEFAULT 80        COMMENT '预警阈值(%)',
  status              TINYINT       NOT NULL DEFAULT 1         COMMENT '状态(1执行 2关闭)',
  lock_version        INT           NOT NULL DEFAULT 0         COMMENT '乐观锁版本',
  create_dept         BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by           BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark              VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_budget_code (budget_code),
  KEY idx_budget_owner (owner_id),
  KEY idx_budget_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算';

-- Immutable budget ledger: submit freezes, approve settles, reject/revoke releases (docs/02 13.4).
CREATE TABLE oa_budget_ledger (
  id             BIGINT        NOT NULL                   COMMENT '流水ID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  budget_id      BIGINT        NOT NULL                   COMMENT '预算ID',
  event_key      VARCHAR(128)  NOT NULL                   COMMENT '业务事件ID(幂等)',
  action         VARCHAR(16)   NOT NULL                   COMMENT '动作(OCCUPY/SETTLE/RELEASE/ADJUST)',
  amount         DECIMAL(14,2) NOT NULL                   COMMENT '金额',
  biz_type       VARCHAR(32)   DEFAULT NULL               COMMENT '业务类型',
  biz_id         BIGINT        DEFAULT NULL               COMMENT '业务ID',
  operator_id    BIGINT        DEFAULT NULL               COMMENT '操作人账号ID',
  create_dept    BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME      DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_budget_event (budget_id, event_key),
  KEY idx_budget_ledger_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算流水';

-- Reimburse claims may reference a budget (checked at submit when expense types are budget-controlled).
ALTER TABLE oa_reimburse_request ADD COLUMN budget_id BIGINT DEFAULT NULL COMMENT '关联预算ID';

-- Menus (ids 2850+ keep clear of HR/workflow P1 2800-2840).
INSERT INTO sys_menu VALUES(2850, '预算管理', 2300, 6, 'budget', 'finance/budget/index', '', 1, 0, 'C', '0', '0', 'fn:budget:list', 'money', 100, 1, sysdate(), null, null, '部门/项目预算与预警');
INSERT INTO sys_menu VALUES(2851, '预算查询', 2850, 1, '', '', '', 1, 0, 'F', '0', '0', 'fn:budget:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2852, '预算新增', 2850, 2, '', '', '', 1, 0, 'F', '0', '0', 'fn:budget:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2853, '预算修改', 2850, 3, '', '', '', 1, 0, 'F', '0', '0', 'fn:budget:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2854, '预算关闭', 2850, 4, '', '', '', 1, 0, 'F', '0', '0', 'fn:budget:remove', '#', 100, 1, sysdate(), null, null, '');

INSERT INTO sys_role_menu VALUES(20, 2850);
INSERT INTO sys_role_menu VALUES(20, 2851);
INSERT INTO sys_role_menu VALUES(20, 2852);
INSERT INTO sys_role_menu VALUES(20, 2853);
INSERT INTO sys_role_menu VALUES(20, 2854);
INSERT INTO sys_role_menu VALUES(23, 2850);
INSERT INTO sys_role_menu VALUES(23, 2851);
INSERT INTO sys_role_menu VALUES(23, 2852);
INSERT INTO sys_role_menu VALUES(23, 2853);
INSERT INTO sys_role_menu VALUES(25, 2850);
INSERT INTO sys_role_menu VALUES(25, 2851);
