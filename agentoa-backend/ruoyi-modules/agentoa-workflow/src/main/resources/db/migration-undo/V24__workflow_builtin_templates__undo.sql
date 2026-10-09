-- V24 回滚（M4 内置模板配套数据）：与 V24__workflow_builtin_templates.sql 一一对应。

-- 新角色的基础待办视图
DELETE FROM sys_role_menu WHERE role_id IN (26, 27);

-- 一键启用按钮
DELETE FROM sys_menu WHERE menu_id = 2920;

-- 行政办公分类
DELETE FROM oa_flow_category WHERE id = 104;

-- 新角色
DELETE FROM sys_role WHERE role_id IN (26, 27);
