-- V23 回滚（M2 流程定义 CRUD）：与 V23__workflow_definition_crud.sql 一一对应。
-- 双向可验证：MigrationReversibilityTest 会核对每个前向对象都在此被还原。

-- 权限按钮
DELETE FROM sys_menu WHERE menu_id IN (2910, 2911, 2912, 2913, 2914);

-- 定义版本扩展
ALTER TABLE oa_flow_definition_version DROP KEY idx_flow_version_source;
ALTER TABLE oa_flow_definition_version DROP COLUMN bpmn_xml;
ALTER TABLE oa_flow_definition_version DROP COLUMN chain_json;

-- 业务类型列还原
ALTER TABLE oa_flow_definition
  MODIFY COLUMN business_type VARCHAR(32) NOT NULL
    COMMENT '业务类型 leave/overtime/correction/reimburse/regularize/offboard';
