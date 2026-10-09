-- AgentOA workflow M5: 通用 OA 申请承接单。
-- 纯 OA 表单/自定义流程不绑定具体业务表，字段差异收敛在 form_data（JSON），
-- 流程结构由定义上的结构化审批链（V23 的 chain_json）决定，由 GenericFlowHandler 统一承接。

CREATE TABLE oa_flow_generic_request (
  id               BIGINT       NOT NULL                   COMMENT '申请ID',
  tenant_id        VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  definition_id    BIGINT       NOT NULL                   COMMENT '目标流程定义ID',
  user_id          BIGINT       NOT NULL                   COMMENT '申请人账号ID',
  title            VARCHAR(255) DEFAULT NULL               COMMENT '申请标题',
  form_data        JSON         DEFAULT NULL               COMMENT '表单数据（按定义的表单 Schema 填写）',
  status           TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2审批中 3已通过 4已拒绝 7已撤销）',
  flow_instance_id BIGINT       DEFAULT NULL               COMMENT '流程实例ID',
  submission_no    INT          NOT NULL DEFAULT 0         COMMENT '提交序号',
  lock_version     INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  create_dept      BIGINT        DEFAULT NULL              COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL              COMMENT '创建人',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL              COMMENT '更新人',
  update_time      DATETIME      DEFAULT NULL              COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_generic_user (user_id, status),
  KEY idx_generic_def (definition_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用 OA 申请（纯表单直接发起）';
