# 模块 1：组织人事开发计划

## 目标与边界

建立所有业务模块共用的组织、岗位、员工和账号来源。P0 包括部门树、岗位、员工基础档案、入职、转正、离职、花名册及 Excel 导入导出；调岗调薪、合同、教育经历、证照和复杂员工自助列为 P1。M0 已有系统用户表，本模块新增员工业务档案，不复制登录账号字段。

## 开发顺序

1. V4 迁移：`oa_employee`、`oa_employee_history`、`oa_onboarding`、`oa_offboarding`、`oa_job_position`；补充 `sys_dept/sys_post` 的业务约束和唯一索引。
2. 定义员工状态：`DRAFT、PROBATION、ACTIVE、LEAVE_PENDING、LEFT、DISABLED`；所有状态变更写历史表。
3. 实现部门/岗位服务：树形查询、负责人、启停用、删除前引用检查。
4. 实现员工档案和工号生成：工号在数据库唯一约束下生成，冲突自动重试；身份证号、手机号按 M0 敏感字段策略保存和展示脱敏。
5. 实现入职、转正、离职命令，预留 Workflow 关联 ID；离职完成后冻结系统账号并注销会话。
6. 实现花名册筛选、分页、导入校验报告、可见字段导出。
7. 前端按组织树、岗位、员工列表、员工详情、生命周期抽屉和导入结果页拆分。

## 权限与规则

| 操作 | 员工 | 部门经理 | HR | 管理员 |
|---|---:|---:|---:|---:|
| 查看本人 | ✓ | ✓ | ✓ | ✓ |
| 查看本部门非敏感字段 | ✓ | ✓ | ✓ | ✓ |
| 编辑员工档案 | 仅自助字段 | 申请 | ✓ | ✓ |
| 组织结构变更 | — | — | ✓ | ✓ |
| 查看身份证/联系方式明文 | — | 授权后 | 按岗位授权 | 单独授权 |
| 导入/导出 | — | — | ✓ | ✓ |

部门和岗位停用不能影响历史记录；删除只允许没有员工、岗位和流程引用的节点。离职账号冻结与离职完成必须同事务，历史审批、考勤和财务记录保留。

## API 与页面

API：`GET/POST/PUT/DELETE /api/v1/hr/departments`、`/positions`、`/employees`；`POST /employees/import`、`GET /employees/export`；`POST /employees/{id}/onboarding`、`/probation`、`/offboarding`；`GET /employees/{id}/history`。所有写接口需要幂等键，导入返回逐行错误，不部分静默成功。

页面：组织树、岗位管理、花名册、员工详情、入职/转正/离职进度、导入预览和导出筛选。移动端先提供本人档案和待办状态，不做完整 HR 管理。

## 测试与退出条件

- 部门循环引用、停用部门仍有员工、岗位重复编码、工号并发生成均被拒绝。
- 导入包含重复工号、非法日期、无效部门、敏感字段权限错误时，返回逐行结果且事务策略明确。
- 转正和离职重复执行只能产生一个生效事件；离职后旧 token、WebSocket 和新业务写入均被拒绝。
- 普通员工无法读取他人敏感字段；经理不能编辑 HR 字段；HR 不能绕过审计。
- 空库/升级迁移、后端测试、前端浏览器主流程和 Excel 文件回归通过。

完成后，输出组织关系样例、角色矩阵、字段字典和 M1 验收报告，才进入流程审批。

## 实施记录（2026-10-02，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 3 节为准，与本文档草案的差异如下（已同步到 09 第 8 节）：

- 路径/权限对齐 05：`/api/v1/hr/departments`（`hr:dept:*`）、`/api/v1/hr/posts`（`hr:post:*`，对应 `oa_job_position`）、`/api/v1/hr/employees`（`hr:employee:*`）；生命周期命令为 `POST /hr/onboard|/hr/regularize|/hr/offboard` 与 `PUT /hr/employees/{id}/status`，变动历史为 `GET /hr/employees/{id}/changes`。
- V4 迁移：`oa_employee`、`oa_employee_history`、`oa_onboarding`、`oa_offboarding`、`oa_job_position`、`oa_idempotency`；`sys_dept/sys_post` 唯一索引；`hr_employee_status` 字典；`hr`/`dept_manager`/`employee` 角色与菜单种子。
- 写命令的幂等键经 `Idempotency-Key` 请求头（同键同请求重放首次结果、同键不同请求 409、保留 24h）；入职/转正/离职命令的生效事件由业务唯一键保证只产生一次，重复执行返回既有结果。
- 状态机落地：`DRAFT/PROBATION/ACTIVE/LEAVE_PENDING/LEFT/DISABLED`，全部变更写 `oa_employee_history`（事件 ID 幂等）；离职/停用同事务冻结 `sys_user` 并注销会话；`LEAVE_PENDING` 供模块 2 流程审批接入。
- 导入：整批校验，存在任一错误行时不落库并返回逐行报告（重复工号、非法日期、无效部门/岗位、无敏感权限写身份证/手机号等）；导出按 `hr:employee:sensitive` 授权分含/不含敏感字段两档。
- 权限矩阵落地并通过测试：员工（本人/本部门非敏感字段/自助联系方式）、部门经理（本部门及以下数据范围）、HR（全量、导入导出、生命周期、敏感字段授权）、管理员；敏感字段由 `@Sensitive` 按 `hr:employee:sensitive` 脱敏。

退出条件证据：空库与升级迁移各一次（V1→V4）；后端测试 34 个（状态机、角色矩阵、导入校验、H2 集成：重复提交/并发工号/越权/事务回滚/账号冻结）；`smoke.mjs` 55 项与 `browser-smoke.mjs` 全部通过；前端生产构建 + `vue-tsc` 通过。

P0 未包含（P1）：调岗调薪、合同、教育经历、证照、复杂员工自助、lifecycle-requests 承接单、入职自动创建登录账号（当前经 `userId` 关联已有账号）。M1 验收报告要点已并入 [09](09-foundation.md) 第 8 节。
