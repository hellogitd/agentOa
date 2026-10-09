-- V26 回滚：与 V26__notice_schedule_list_perm.sql 一一对应。

DELETE FROM sys_role_menu WHERE role_id = 20 AND menu_id = 2930;
DELETE FROM sys_menu WHERE menu_id = 2930;
