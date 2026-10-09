-- P1 批次二 · 交付包 E（AT-06 调休批次滚动过期）。
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 16 节）。

-- ---------------------------------------------------------------- leave quota batches

CREATE TABLE oa_leave_batch (
  id              BIGINT       NOT NULL                   COMMENT '批次ID',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id         BIGINT       NOT NULL                   COMMENT '账号ID',
  year            INT          NOT NULL                   COMMENT '归属额度年度（与 oa_leave_balance 对齐）',
  leave_type      VARCHAR(32)  NOT NULL                   COMMENT '假种编码（调休/年假批次管理）',
  batch_no        VARCHAR(64)  NOT NULL                   COMMENT '批次号（展示）',
  grant_minutes   INT          NOT NULL DEFAULT 0         COMMENT '发放分钟（回收时条件缩减）',
  frozen_minutes  INT          NOT NULL DEFAULT 0         COMMENT '冻结分钟（过期保护至结算）',
  used_minutes    INT          NOT NULL DEFAULT 0         COMMENT '已用分钟',
  expired_minutes INT          NOT NULL DEFAULT 0         COMMENT '已过期清零分钟',
  valid_from      DATE         DEFAULT NULL               COMMENT '有效起始日',
  expire_date     DATE         DEFAULT NULL               COMMENT '有效截止日（当日有效，次日起过期）',
  event_key       VARCHAR(128) NOT NULL                   COMMENT '创建事件（幂等去重）',
  status          TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1有效 2已过期 3已用尽）',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_event (event_key),
  KEY idx_batch_user_type (user_id, leave_type, expire_date),
  KEY idx_batch_due (status, expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期额度批次（滚动过期，FIFO 先过期先扣）';

CREATE TABLE oa_leave_batch_allocation (
  id               BIGINT       NOT NULL                   COMMENT '分配ID',
  batch_id         BIGINT       NOT NULL                   COMMENT '批次ID',
  ledger_event_key VARCHAR(128) NOT NULL                   COMMENT '关联 oa_leave_ledger.event_key',
  minutes          INT          NOT NULL                   COMMENT '分配分钟',
  action           VARCHAR(16)  NOT NULL                   COMMENT '动作（FREEZE/SETTLE/RELEASE/EXPIRE）',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_alloc_batch (batch_id),
  KEY idx_alloc_event (ledger_event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次消耗分配表（冻结/结算/释放/过期分摊）';

-- ---------------------------------------------------------------- history backfill

-- 历史余额迁移为单批次（调休/年假）：valid_from=当年 1/1，expire_date=原值（空则当年 12/31）。
-- id 由来源额度行给出（oa_leave_batch.id 无自增，必须显式提供，否则 MySQL 报 1364）
INSERT INTO oa_leave_batch(id, user_id, year, leave_type, batch_no, grant_minutes, frozen_minutes, used_minutes,
                           expired_minutes, valid_from, expire_date, event_key, status)
SELECT b.id, b.user_id, b.year, b.leave_type,
       CONCAT('B', b.year, '0101-', LPAD(b.id, 6, '0')),
       b.total_minutes, b.frozen_minutes, b.used_minutes, 0,
       MAKEDATE(b.year, 1),
       COALESCE(b.expire_date, LAST_DAY(CONCAT(b.year, '-12-01'))),
       CONCAT('MIGRATE:', b.id), 1
FROM oa_leave_balance b
WHERE b.leave_type IN ('compensatory', 'annual')
  AND b.total_minutes > 0;

-- 批次消费条件不受 expire_date 影响历史冻结的结算/释放（allocation 缺失时按冻结 FIFO 兜底）。
