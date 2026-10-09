-- P1 批次二 · 交付包 D（CL-01 重复日程 RRULE）
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 16 节）

-- 重复规则扩展：RRULE 子集（FREQ/INTERVAL/BYDAY/BYMONTHDAY/COUNT/UNTIL）
ALTER TABLE oa_calendar_event MODIFY repeat_rule VARCHAR(255) DEFAULT NULL COMMENT '重复规则（RRULE 子集）';

-- 主记录 + 预物化实例：series_id 指向主记录（0=非重复），is_exception 标记脱离规则的实例
ALTER TABLE oa_calendar_event ADD COLUMN series_id BIGINT DEFAULT 0 COMMENT '系列主记录 ID（0=非重复）';
ALTER TABLE oa_calendar_event ADD COLUMN repeat_until DATETIME DEFAULT NULL COMMENT '重复截止日期';
ALTER TABLE oa_calendar_event ADD COLUMN repeat_count INT DEFAULT NULL COMMENT '重复次数上限';
ALTER TABLE oa_calendar_event ADD COLUMN is_exception TINYINT DEFAULT 0 COMMENT '是否已脱离规则的实例（1=是）';

CREATE INDEX idx_event_series ON oa_calendar_event (series_id);
