-- Keep the inherited identity, dictionary and audit capabilities only.
UPDATE sys_menu SET status='1' WHERE menu_id IN (4,5,107,123) OR parent_id IN (4,5,107,123)
  OR component LIKE 'workflow/%' OR component LIKE 'demo/%'
  OR component LIKE 'system/oss%' OR component LIKE 'monitor/admin%' OR component LIKE 'monitor/snail%';
DELETE FROM sys_role_menu WHERE role_id<>1;
DELETE FROM sys_role_dept WHERE role_id<>1;
DELETE FROM sys_role WHERE role_id<>1;
UPDATE sys_dept SET dept_name='AgentOA', leader=NULL WHERE dept_id=100;
UPDATE sys_dept SET dept_name='管理部',parent_id=100,ancestors='0,100',leader=NULL WHERE dept_id=103;
DELETE FROM sys_dept WHERE dept_id NOT IN (100,103);
UPDATE sys_post SET post_name='系统管理',post_code='system_admin' WHERE post_id=1;
DELETE FROM sys_post WHERE post_id<>1;
DELETE FROM sys_notice;
