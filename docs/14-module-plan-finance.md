# 模块 4：报销财务开发计划

## 目标与边界

实现人民币报销、发票占用和人工付款登记。P0 包括费用类型、报销单/明细、发票图片、审批、付款、基础统计；预算、OCR、真伪校验、银行支付、会计总账和银行流水列为 P1/P2。

## 开发顺序

1. V7 迁移：`oa_expense_type`、`oa_expense_claim`、`oa_expense_item`、`oa_invoice`、`oa_invoice_reservation`、`oa_payment_record`、`oa_finance_event`。
2. 实现金额对象：API 采用两位小数字符串，服务端转换为最小货币单位整数；明细汇总是唯一总额来源。
3. 实现发票规范化和唯一占用：发票类型、代码（无代码为空串）、号码、金额和日期组成可审计指纹；在途/已付款占用，拒绝或撤销释放。
4. 接入报销流程分支：≤1000 主管→财务，≤5000 增加出纳，>5000 增加总监；流程定义由 Workflow 管理，金额规则由财务服务生成。
5. 实现已通过未付款单的人工全额付款登记，付款状态条件更新，禁止重复付款。
6. 实现财务列表、明细、发票关联、付款登记、导出和部门/类型统计。

## 权限与安全

员工只能创建和查看自己的报销；经理查看授权部门；财务审核和登记付款；出纳只能登记付款；管理员不能自动获得发票明文和付款权限。发票文件沿用 M0 私有文件，下载需同时满足报销单授权和文件所有者/绑定关系。

API：`GET/POST /api/v1/finance/types`、`POST /claims`、`GET /claims/mine`、`GET /claims/{id}`、`POST /claims/{id}/invoices`、`POST /claims/{id}/pay`、`GET /invoices/search`、`GET /reports/expense`、`GET /reports/export`。

## 测试与退出条件

- 明细合计不等于主表总额、负数、超过精度和非法币种均拒绝。
- 同一发票并发提交只能被一个在途单占用；拒绝/撤销后可按规则重新占用，付款后永久禁止重复使用。
- 付款只能发生一次且金额等于已审批金额；重复请求返回首次结果。
- 发起人、经理、财务、出纳之间无越权查看、下载、审批和付款。
- 审批拒绝、撤销、文件上传失败、流程启动失败均不留下错误占用；统计能与明细对账。

## 实施记录（2026-10-04，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 6 节为准，与本文档草案的差异如下（已同步到 09 第 11 节）：

- 路径/权限对齐 05：`/api/v1/finance/expense-types`（`fn:expense-type:*`）、`/invoices`（含 `/{id}/download` 发票图片，`fn:invoice:query`）、`/reimburses`（`POST /{id}/pay` 付款唯一写入口 `fn:payment:add`、`GET /pending` 待付款清单 `fn:payment:list`）、`/payments`（`fn:payment:list`）、`/reports/expense|export`（`fn:report:list|export`）；报销草稿 CRUD/提交沿用模块 2 承接单接口（同一路径前缀，`POST /reimburses`、`PUT/DELETE /{id}`、`POST /{id}/submit`）。
- V7 迁移：`oa_expense_type`、`oa_invoice`、`oa_invoice_reservation`、`oa_expense_item`、`oa_payment_record`、`oa_finance_event`（`oa_` 前缀按 V4 惯例统一 docs/04 的 `fn_*` 草案）；docs/14 V7 清单中的 `oa_expense_claim` 沿用模块 2 的 `oa_reimburse_request` 承接单（先例同 M3），`oa_expense_item` 为提交时固化的财务明细快照；`fn_invoice_type`/`fn_payment_method`/`fn_pay_status`/`fn_finance_action` 字典、五个费用类型种子、`fn:*` 菜单（2300–2317）与 `hr`/`dept_manager`/`employee`/`finance`/`cashier`/`director` 角色授权。空库 V1→V7 与升级 V6→V7 各执行一次通过。
- 金额对象：API 两位小数字符串，服务端 `MoneyUtil` 转最小货币单位（分）参与校验（格式/负数/超精度拒绝），落库沿用 `DECIMAL(12,2)`（与 V5 承接单 `total_amount` 一致，docs/14 第 2 步口径的落地差异）。
- 发票占用：身份（类型+代码+号码）唯一，规范化 SHA-256 指纹留审计；提交时按固定 ID 顺序条件更新占用（`occupied_reimburse_id`），同一发票不得分摊到多条明细或多单（`FINANCE_INVOICE_MULTI_USE/OCCUPIED/PAID`）；拒绝/撤销释放，付款后经 `paid_reimburse_id` 永久占用；占用/释放/付款写 `oa_invoice_reservation` 与 `oa_finance_event`（`event_key` 唯一幂等）。明细金额超发票金额返回 400。
- 流程回调：模块 4 注册 `FinanceFlowListener implements FlowEventListener`（编排事务内执行）：提交前校验归属/金额/占用，提交时固化 `oa_expense_item` 快照并占用发票，通过后状态 3→5（待付款，V5 预留的 5/6 语义由财务模块持有），拒绝/撤销释放占用；报销拒绝/撤销后发票可重新占用，付款后永久禁止重复使用。
- 付款登记：`POST /reimburses/{id}/pay` 要求 `Idempotency-Key` + `lockVersion` + 金额等于已审批金额（分核对）；`oa_payment_record` 的 `uk_payment_reimburse` 保证一次全额付款，重复请求（同键）返回首次结果、异键 409 `FINANCE_ALREADY_PAID`；付款条件更新承接单 3/5→6、发票转永久占用、写财务事件与申请人 outbox 付款通知。按 docs/14 权限规则，付款登记与发票明文显式绑定 `finance`/`cashier` 角色（管理员不自动获得，越权 403）。
- 对前置模块的登记变更（docs/10 变更清单）：模块 2 承接单 `ReimburseDetailBo` 增加可选 `invoiceId`/`expenseTypeId`（写入 details_json，兼容原 `invoiceNo`），并补 `DELETE /api/v1/finance/reimburses/{id}` 草稿删除（owner + 仅草稿/可编辑态）。
- 前端 `views/finance/{reimburse,invoice,expense-type,payment,report}` + `api/finance`：我的报销（明细行编辑、发票选择、提交/删除）、发票录入/占用状态/图片下载、费用类型树维护、待付款清单与付款登记、部门/费用类型统计与 Excel 导出。

退出条件证据：空库迁移与升级迁移各一次（V1→V8 范围内 V7 段，本机 `docker compose` migrate 服务实测）；后端测试 20 个（金额对象、权限矩阵、H2 集成：发票占用/并发单占用/拒绝撤销释放/付款永久占用/越权与超发票金额/重复引用拒绝、付款一次/幂等重放/异键 409/金额不符 400/状态不符与版本冲突 409/付款通知 outbox）；`smoke.mjs` 240+ 项全部通过（新增 M4 契约：费用类型重复编码 409、发票身份重复 409、明细越权 403、提交占用与双单冲突 409、拒绝释放、待付款 5、金额不符 400、员工/管理员付款 403、出纳付款/重放/二次付款 409、付款列表/待付款/报表权限）；`browser-smoke.mjs` 通过（含我的报销/发票管理页面渲染）；前端 `build:prod` + `vue-tsc` 通过。

P0 未包含（P1/P2）：预算（`fn_budget` 未建表）、OCR 与发票真伪校验、银行支付对接、会计总账与银行流水、部分付款与拆票、发票图片的财务侧批量检索；统计的部门层级钻取与预算预警随模块 8 报表承接。

