-- P1 批次二 · 交付包 B（FN-05/FN-02 部分付款/拆票）
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 16 节）

-- 部分付款：删除一单一付唯一约束，改为普通索引；新增已付累计列
ALTER TABLE oa_payment_record DROP INDEX uk_payment_reimburse;
ALTER TABLE oa_payment_record ADD INDEX idx_payment_reimburse (reimburse_id);

ALTER TABLE oa_reimburse_request ADD COLUMN paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已付金额（部分付款累计）';

-- 拆票/跨单分摊：分配表承载发票到明细/报销单的分摊记录
CREATE TABLE oa_invoice_allocation (
  id             BIGINT        NOT NULL                   COMMENT '分配ID',
  tenant_id      VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  invoice_id     BIGINT        NOT NULL                   COMMENT '发票ID',
  reimburse_id   BIGINT        NOT NULL                   COMMENT '报销单ID',
  expense_item_id BIGINT       DEFAULT NULL               COMMENT '费用明细ID（可空=整单分摊）',
  amount         DECIMAL(12,2) NOT NULL                   COMMENT '分摊金额',
  event_key      VARCHAR(128)  NOT NULL                   COMMENT '业务事件ID（幂等去重）',
  operator_id    BIGINT        NOT NULL                   COMMENT '操作账号ID',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_allocation_event (event_key),
  KEY idx_allocation_invoice (invoice_id),
  KEY idx_allocation_reimburse (reimburse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票分摊（拆票/跨单分摊/部分付款）';

-- 放开同一发票在同单多明细引用限制（由 allocation 表管理分摊）
ALTER TABLE oa_expense_item DROP INDEX uk_item_reimburse_invoice;
ALTER TABLE oa_expense_item ADD INDEX idx_item_reimburse_invoice (reimburse_id, invoice_id);
