-- AgentOA workflow M2: 流程定义 CRUD（结构化审批链配置 → 受控 BPMN）。
-- API contract: docs/05-api-spec.md section 4.1/4.2.
-- 只开放结构化审批链配置，不开放 BPMN 上传：bpmn_xml 存放 FlowChainCompiler 的编译产物，
-- bpmn_resource 仍是来源标识（代码仓库模板=classpath 路径；编译产物=chain://<processKey>/v<versionNo>）。

-- ------------------------------------------------------------------ 定义版本扩展

ALTER TABLE oa_flow_definition_version
  ADD COLUMN bpmn_xml   MEDIUMTEXT DEFAULT NULL COMMENT '编译产物 BPMN（结构化审批链编译结果；代码仓库模板为空）',
  ADD COLUMN chain_json JSON       DEFAULT NULL COMMENT '结构化审批链配置 JSON（含 cc 节点，实例启动时生成抄送记录）';

-- 编译产物版本查询：按来源标识快速区分
ALTER TABLE oa_flow_definition_version
  ADD KEY idx_flow_version_source (bpmn_resource(64));

-- 业务类型放开为自由标识（26 个内置模板 + 纯 OA 表单走 generic），注释同步
ALTER TABLE oa_flow_definition
  MODIFY COLUMN business_type VARCHAR(32) NOT NULL DEFAULT 'generic'
    COMMENT '业务类型标识（leave/overtime/... 或 generic）';

-- ------------------------------------------------------------------ 权限按钮

-- 流程定义删除 / 表单管理（docs/05 section 4.1/4.2 的 wf:definition:remove、wf:form:*）
INSERT INTO sys_menu VALUES(2910, '流程删除', 2105, 8,  '', '', '', 1, 0, 'F', '0', '0', 'wf:definition:remove', '#', 100, 1, sysdate(), null, null, '删除未被实例引用的流程定义');
INSERT INTO sys_menu VALUES(2911, '表单查询', 2105, 9,  '', '', '', 1, 0, 'F', '0', '0', 'wf:form:query',      '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2912, '表单新增', 2105, 10, '', '', '', 1, 0, 'F', '0', '0', 'wf:form:add',       '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2913, '表单修改', 2105, 11, '', '', '', 1, 0, 'F', '0', '0', 'wf:form:edit',      '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2914, '表单列表', 2105, 12, '', '', '', 1, 0, 'F', '0', '0', 'wf:form:list',      '#', 100, 1, sysdate(), null, null, '');
