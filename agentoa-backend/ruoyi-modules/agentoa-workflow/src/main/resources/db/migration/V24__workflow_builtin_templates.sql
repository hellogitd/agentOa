-- AgentOA workflow M4: 26 个内置模板的配套数据。
-- 模板本体（表单设计 + 推荐默认审批链）在代码仓库 workflow/templates/builtin-templates.json，
-- 由 BuiltInTemplateCatalog 载入、TemplateRegistrar 编译注册，因此校验摘要保持真实，不在迁移里造数据。
-- 这里只补模板依赖的角色、分类与权限按钮。

-- ------------------------------------------------------------------ 新角色（审批链 ROLE:admin / ROLE:gm 依赖）

INSERT INTO sys_role VALUES(26, '000000', '行政',   'admin', 8, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '行政审批（用印/办公用品/用车/通用申请）');
INSERT INTO sys_role VALUES(27, '000000', '总经理', 'gm',    9, '1', 1, 1, '0', '0', 100, 1, sysdate(), null, null, '总经理审批（大额/重要事项）');

-- ------------------------------------------------------------------ 行政办公分类（builtin-templates.json categoryId=104）

INSERT INTO oa_flow_category VALUES(104, '000000', 'ADMIN', '行政办公', 5, '0', 100, 1, sysdate(), null, null, '用印、办公用品、出差、公务用车');

-- ------------------------------------------------------------------ 一键启用内置模板

INSERT INTO sys_menu VALUES(2920, '一键启用模板', 2105, 13, '', '', '', 1, 0, 'F', '0', '0', 'wf:definition:enable', '#', 100, 1, sysdate(), null, null, '一键启用全部内置流程模板');

-- ------------------------------------------------------------------ 新角色的基础待办视图（与 hr/finance 等业务角色一致）

INSERT INTO sys_role_menu VALUES(26, 2100);
INSERT INTO sys_role_menu VALUES(26, 2101);
INSERT INTO sys_role_menu VALUES(26, 2102);
INSERT INTO sys_role_menu VALUES(26, 2103);
INSERT INTO sys_role_menu VALUES(26, 2104);
INSERT INTO sys_role_menu VALUES(26, 2834);
INSERT INTO sys_role_menu VALUES(26, 2840);
INSERT INTO sys_role_menu VALUES(27, 2100);
INSERT INTO sys_role_menu VALUES(27, 2101);
INSERT INTO sys_role_menu VALUES(27, 2102);
INSERT INTO sys_role_menu VALUES(27, 2103);
INSERT INTO sys_role_menu VALUES(27, 2104);
INSERT INTO sys_role_menu VALUES(27, 2834);
INSERT INTO sys_role_menu VALUES(27, 2840);
