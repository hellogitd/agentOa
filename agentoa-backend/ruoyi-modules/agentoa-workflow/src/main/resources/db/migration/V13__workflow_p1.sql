-- AgentOA workflow P1: suspend/terminate flag, cc records, delegation, timeout marker.
-- API contract: docs/05-api-spec.md section 4.4/4.5. Tables extend docs/04-database-design.md section 4.

-- Timeout is an independent flag (docs/03 3.2), not a status transition.
ALTER TABLE oa_flow_instance ADD COLUMN is_timeout TINYINT NOT NULL DEFAULT 0 COMMENT '是否超时提醒过';

-- CC records (GET /api/v1/wf/tasks/cc data source). event_id keeps delivery idempotent.
CREATE TABLE oa_flow_cc (
  id                  BIGINT       NOT NULL                   COMMENT '抄送ID',
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  instance_id         BIGINT       NOT NULL                   COMMENT '流程实例ID',
  sender_user_id      BIGINT       NOT NULL                   COMMENT '抄送人账号ID',
  cc_user_id          BIGINT       NOT NULL                   COMMENT '被抄送人账号ID',
  comment             VARCHAR(500) DEFAULT NULL               COMMENT '抄送留言',
  event_id            VARCHAR(64)  NOT NULL                   COMMENT '业务事件ID(幂等)',
  read_time           DATETIME     DEFAULT NULL               COMMENT '查看时间',
  create_dept         BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by           BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time         DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cc_event (event_id),
  KEY idx_cc_instance (instance_id),
  KEY idx_cc_user (cc_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送记录';

-- Delegation (WF-12): owner delegates approval rights to another user for a validity window.
CREATE TABLE oa_flow_delegate (
  id             BIGINT       NOT NULL                   COMMENT '委托ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  owner_id       BIGINT       NOT NULL                   COMMENT '委托人账号ID',
  delegate_id    BIGINT       NOT NULL                   COMMENT '受托人账号ID',
  start_date     DATE         NOT NULL                   COMMENT '开始日期',
  end_date       DATE         NOT NULL                   COMMENT '结束日期',
  process_keys   VARCHAR(255) DEFAULT NULL               COMMENT '适用流程(逗号分隔,空=全部)',
  status         TINYINT      NOT NULL DEFAULT 1         COMMENT '状态(1启用 0停用)',
  create_dept    BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_delegate_owner (owner_id),
  KEY idx_delegate_target (delegate_id),
  KEY idx_delegate_date (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程委托代理';

-- Task action dictionary entries for P1 actions (data ids 460+ avoid the 420-427 leave range).
INSERT INTO sys_dict_data VALUES(460, '000000', 5, '退回', 'return', 'wf_task_action', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(461, '000000', 6, '加签', 'addsign', 'wf_task_action', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(462, '000000', 7, '催办', 'urge', 'wf_task_action', '', 'info', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(463, '000000', 8, '抄送', 'cc', 'wf_task_action', '', 'info', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(464, '000000', 9, '挂起', 'suspend', 'wf_task_action', '', 'info', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(465, '000000', 10, '恢复', 'resume', 'wf_task_action', '', 'info', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(466, '000000', 11, '终止', 'terminate', 'wf_task_action', '', 'danger', 'N', 100, 1, sysdate(), null, null, 'P1');

-- Menus (ids 2830+ keep clear of HR P1 2800-2829).
INSERT INTO sys_menu VALUES(2830, '流程监控', 2100, 6, 'monitor', 'workflow/monitor/index', '', 1, 0, 'C', '0', '0', 'wf:instance:list', 'monitor', 100, 1, sysdate(), null, null, '全部流程实例监控与强制终止');
INSERT INTO sys_menu VALUES(2831, '流程挂起', 2830, 1, '', '', '', 1, 0, 'F', '0', '0', 'wf:instance:suspend', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2832, '流程恢复', 2830, 2, '', '', '', 1, 0, 'F', '0', '0', 'wf:instance:resume', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2833, '强制终止', 2830, 3, '', '', '', 1, 0, 'F', '0', '0', 'wf:instance:terminate', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2834, '抄送我的', 2100, 7, 'cc', 'workflow/cc/index', '', 1, 0, 'C', '0', '0', '', 'message', 100, 1, sysdate(), null, null, '抄送我的流程');
INSERT INTO sys_menu VALUES(2840, '委托代理', 2100, 8, 'delegate', 'workflow/delegate/index', '', 1, 0, 'C', '0', '0', '', 'peoples', 100, 1, sysdate(), null, null, '审批委托代理设置');

-- Role-menu matrix: HR and managers monitor; every business role gets cc and delegation.
INSERT INTO sys_role_menu VALUES(20, 2830);
INSERT INTO sys_role_menu VALUES(20, 2831);
INSERT INTO sys_role_menu VALUES(20, 2832);
INSERT INTO sys_role_menu VALUES(20, 2833);
INSERT INTO sys_role_menu VALUES(20, 2834);
INSERT INTO sys_role_menu VALUES(20, 2840);
INSERT INTO sys_role_menu VALUES(21, 2830);
INSERT INTO sys_role_menu VALUES(21, 2834);
INSERT INTO sys_role_menu VALUES(21, 2840);
INSERT INTO sys_role_menu VALUES(22, 2834);
INSERT INTO sys_role_menu VALUES(22, 2840);
INSERT INTO sys_role_menu VALUES(23, 2834);
INSERT INTO sys_role_menu VALUES(23, 2840);
INSERT INTO sys_role_menu VALUES(24, 2834);
INSERT INTO sys_role_menu VALUES(24, 2840);
INSERT INTO sys_role_menu VALUES(25, 2834);
INSERT INTO sys_role_menu VALUES(25, 2840);
