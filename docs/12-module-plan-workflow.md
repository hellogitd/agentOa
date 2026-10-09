# 模块 2：流程审批开发计划

## 目标与边界

在 Flowable 7.2.0 上实现受控流程平台。P0 只支持固定 BPMN 模板、串行用户任务、排他网关、受控服务任务、表单版本、发起、同意、拒绝、转办、撤销、待办/已办/我发起和流程图；在线拖拽设计器、会签、或签、任意退回、加签、代理、挂起和超时升级列为 P1。

## 开发顺序

1. V5 迁移：`oa_flow_category`、`oa_flow_definition`、`oa_flow_definition_version`、`oa_flow_form_version`、`oa_flow_instance`、`oa_flow_task_action`、`oa_flow_business_ref`、`oa_flow_idempotency`。
2. 建立模板注册机制：BPMN、表单 JSON Schema、版本号、状态 `DRAFT/PUBLISHED/RETIRED`、校验摘要；只允许代码仓库和管理员批准的模板发布。
3. 建立办理人解析器：本人、直属主管、部门负责人、指定角色和白名单成员；禁止任意 SpEL、Bean 调用和用户脚本。
4. 实现流程编排事务：保存业务主表、启动指定版本实例、保存业务关联、写 outbox 必须在同一事务；失败全部回滚。
5. 实现任务动作和历史：同意、拒绝、转办、撤销，保存操作者、旧办理人、新办理人、意见和时间。
6. 接入六个 P0 模板：请假、加班、补卡、报销、转正、离职。业务规则由业务模块拥有，流程模块只负责通用编排。
7. 前端实现模板列表、表单渲染、待办、已办、我发起、详情和流程图；不开放任意 BPMN 上传。

## 权限、版本与一致性

发起人只能查看自己的实例和被授权的抄送；办理人只能处理当前未完成任务；管理员可以监控但不能默认修改业务结果。发布新版本不能改变历史实例的表单、BPMN 和办理人快照。撤销、拒绝和业务补偿由业务模块注册回调，不能由通用流程服务直接修改考勤或财务账本。

API：`GET/POST /api/v1/workflows/categories`、`/definitions`、`/definitions/{id}/publish`、`/forms/{version}`、`POST /instances`、`GET /tasks/todo`、`/tasks/done`、`/instances/mine`、`POST /tasks/{id}/approve`、`/reject`、`/transfer`、`POST /instances/{id}/cancel`、`GET /instances/{id}/diagram`。

## 测试与退出条件

- 版本发布后启动的实例固定版本；新版本发布不影响旧实例。
- 同一 `Idempotency-Key` 重复发起只产生一个业务单和流程实例；不同请求摘要返回 409。
- 无办理权限、已完成任务、他人实例和伪造办理人均返回 403/404，不泄露数据。
- 流程启动失败时业务记录、Flowable 实例、关联表和 outbox 全部回滚。
- 六个固定模板各有正常、拒绝、撤销、分支和历史详情用例；H2 事务测试与 MySQL 集成测试通过。

## 暂缓项

在线设计器必须等固定模板运行稳定后另立设计文档；不能把 Warm-Flow 页面当作 Flowable 功能，也不能在 P0 中允许用户输入任意表达式。



## 实施记录（2026-10-03，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 4 节为准，与本文档草案的差异如下（已同步到 09 第 9 节）：

- 路径/权限对齐 05：`/api/v1/wf/categories|definitions|forms|instances|tasks`（`instances/start`、`tasks/{id}/complete|transfer`、`instances/{id}/revoke`）；本文档的 `/api/v1/workflows/*`（`approve/reject/cancel`）草案路径不再使用。权限串 `wf:definition:*`、`wf:category:*`、`wf:instance:list`（全部实例监控范围）；待办/已办/我发起对登录用户开放。
- V5 迁移：`oa_flow_category`、`oa_flow_definition`、`oa_flow_definition_version`、`oa_flow_form_version`、`oa_flow_instance`、`oa_flow_task_action`、`oa_flow_business_ref`、`oa_flow_idempotency`，并按退出条件补六模板最小承接单 `oa_leave_request`、`oa_overtime_request`、`oa_correction_request`、`oa_reimburse_request`、`oa_lifecycle_request`（docs/04 的 `at_/fn_/hr_lifecycle_request` 草案按 V4 惯例统一 `oa_` 前缀）；字典、`finance/cashier/director` 角色与 `wf:*` 菜单种子。模板/表单行不进种子 SQL，由代码仓库 `TemplateRegistrar` 启动注册并发布。
- 模板注册机制：BPMN + 表单 JSON Schema + 版本号 + `DRAFT/PUBLISHED/RETIRED` + SHA-256 校验摘要；受控校验白名单（start/userTask/exclusiveGateway/end、固定 AssigneeTaskListener、白名单条件与办理人规则），拒绝脚本任务、任意表达式与外部监听器类；代码模板变更自动新增版本并发布，历史实例固定原定义/表单/办理人快照。
- 办理人解析器：`SELF`（发起人）、`LEADER`（`oa_employee.direct_leader_id`，缺失回退部门负责人）、`DEPT_HEAD`（`sys_dept.leader`）、`ROLE:key`（角色成员候选人）、`WHITELIST`、`USER_SELECT`（发起人自选：发起页选定，`signMode=SINGLE` 选 1 人、`COUNTERSIGN/EITHERSIGN` 选 ≥1 人，结果走流程变量 `userSelect_<nodeId>`，缺失确定性报错不回退任意人）；禁止任意 SpEL、Bean 调用与用户脚本，解析失败 400 且编排整体回滚。
- 幂等：`oa_flow_idempotency`（用户 + Key，24h）覆盖发起/提交/同意/拒绝/转办/撤销；同键同请求返回首次结果（含任务结果重放），同键异请求 409；超长事件 ID 以确定性摘要写入 `sys_outbox.event_id`（VARCHAR(64)）。
- 任务动作与终态：`complete` 仅接受 `agree/reject`；拒绝终止实例；撤销仅发起人且无人处理过；转办记录旧/新办理人且任务保持待处理；通过/拒绝/撤销经 `FlowBusinessHandler` 回调驱动业务承接单与 HR 状态机，通用流程服务不直接修改考勤/财务账本。
- 版本固定：实例启动固定 `definition_version_id` 与表单快照，`startProcessInstanceById` 绑定发布版本；新版本发布不影响历史实例（H2 与 smoke 均有断言）。

退出条件证据：空库迁移与升级迁移各一次（V1→V5，本机 `docker compose` migrate 服务实测）；后端测试 20 个（BPMN 受控校验攻击用例、授权矩阵、H2 集成：六模板正常/拒绝/撤销/转办/报销三分支、幂等重放与 409、越权 403 与 404、办理人解析失败回滚、版本固定、生命周期审批驱动 HR 状态机与账号冻结）；`smoke.mjs` 159 项与 `browser-smoke.mjs` 全部通过；前端生产构建 + `vue-tsc` 通过。

P0 未包含（P1）：在线拖拽设计器、会签、或签、任意退回、加签、代理、挂起/恢复/终止、催办、抄送、超时升级、BPMN 上传导入；请假额度冻结与发票占用（模块 3/4 经回调接入）；转正/离职未来生效日期的调度执行。M2 验收报告要点已并入 [09](09-foundation.md) 第 9 节。

## 变更清单（2026-10-08，[23](23-module-plan-h5.md) H5-H3-01）

| 变更 | 说明 | 影响 |
|---|---|---|
| `FormSchemaBo.FIELD_TYPES` 白名单增 `file`/`image` | 表单附件字段类型（值 = sys_file ID 字符串）；`FormSchemaSupport` 其余校验规则不变（选项仅下拉、子字段仅明细） | 兼容旧 schema（只增不改）；PC FormDesigner 调色板与 `FormSchema.vue` 渲染同步支持（上传走 axios 服务带鉴权） |
| 新增 `GET /api/v1/wf/instances/{instanceId}/attachments/{fileId}/download` | 表单附件授权下载：实例可见性（`selectDetail`，同详情口径）+ 表单数据实际引用该文件（`FlowAttachmentAccess.referencedInForm` 递归标量匹配，防 ID 枚举）双重判定；字节读取 `FlowAttachmentDownloader`（sys_file + 私有 S3，口径同 finance 发票图） | 新增端点，无契约变更；越权/未引用/文件缺失均 404（对象权限口径）；H2 回归 `FlowAttachmentAccessTest` 7 例 |