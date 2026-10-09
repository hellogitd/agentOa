-- AgentOA workflow: 发起申请目录同步（H5 pages/approval/apply）权限补缺
-- GET /api/v1/wf/forms/{formKey} 要求 wf:form:query（V23 只建了按钮 2911，从未授权），
-- 导致除超管外所有角色读取表单模板均 403（H5「表单模板不可用」、管理端「我发起的」选模板失败）。
-- 与 V26 补 nt:schedule:list 的做法一致：授予全部业务角色。
-- 20 hr / 21 dept_manager / 22 employee / 23 finance / 24 cashier / 25 director / 26 admin / 27 gm
INSERT INTO sys_role_menu VALUES(20, 2911);
INSERT INTO sys_role_menu VALUES(21, 2911);
INSERT INTO sys_role_menu VALUES(22, 2911);
INSERT INTO sys_role_menu VALUES(23, 2911);
INSERT INTO sys_role_menu VALUES(24, 2911);
INSERT INTO sys_role_menu VALUES(25, 2911);
INSERT INTO sys_role_menu VALUES(26, 2911);
INSERT INTO sys_role_menu VALUES(27, 2911);
