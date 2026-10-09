-- P1 批次二 · 交付包 A（WF-08 会签/或签多实例模板）。
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 16 节）。

-- 多实例并行待办：当前办理人快照可能拼接 N 人，扩长避免截断。
ALTER TABLE oa_flow_instance MODIFY current_assignees VARCHAR(512) DEFAULT NULL COMMENT '当前办理人（逗号分隔，展示快照；多实例并行可多人）';
