-- V27 回滚（发起申请目录同步权限补缺）：与 V27__workflow_form_query_grant.sql 一一对应。
-- 双向可验证：MigrationReversibilityTest 会核对每个前向对象都在此被还原。
DELETE FROM sys_role_menu WHERE menu_id = 2911;
