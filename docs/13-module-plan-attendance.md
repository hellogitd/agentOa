# 模块 3：考勤打卡开发计划

## 目标与边界

实现固定班和基础 PC/H5 打卡。P0 包括考勤组、班次、工作日历、上/下班打卡、迟到早退判定、请假额度、加班、补卡和月报；轮班、外勤 GPS/照片、WiFi、反作弊、申诉和复杂假期政策列为 P1/P2。

## 开发顺序

1. V6 迁移：`oa_attendance_group`、`oa_attendance_member`、`oa_shift`、`oa_calendar`、`oa_holiday`、`oa_punch_record`、`oa_attendance_day`、`oa_leave_type`、`oa_leave_balance`、`oa_leave_ledger`、`oa_overtime`、`oa_correction`。
2. 实现时区和工作日历服务：所有存储 UTC，展示使用组织时区；跨夜班归属班次开始日期。
3. 实现班次和考勤组生效版本；生效后的历史日报使用快照，不随规则修改重算。
4. 实现打卡命令：服务器接收时间、设备、IP、来源，保存原始记录；有效首次/末次记录由规则计算。
5. 实现请假额度分钟账本：提交冻结、通过转已用、拒绝/撤销释放，条件更新防透支。
6. 接入 Workflow 的请假、加班、补卡模板；通过回调更新账本和考勤日汇总。
7. 实现月报、异常列表、导出和员工个人余额页面。

## 核心规则

请假时长由服务端根据工作日历、班次、休息段计算，API 不接受客户端计算结果作为事实。每条账本事件带业务事件 ID，重复消费不得重复扣减。打卡不因迟到而丢弃原始记录；迟到、早退、旷工是计算结果。当前不承诺法定年假规则，假期参数必须由 HR 确认后配置。

API：`GET/POST /api/v1/attendance/groups`、`/shifts`、`/calendar`、`POST /punches`、`GET /punches/today`、`GET /days`、`GET /leave/balances`、`GET /leave/ledger`、`GET /reports/monthly`、`GET /reports/export`、`POST /corrections`。打卡接口按用户+日期+类型限制重复请求并支持客户端幂等键。

页面：考勤组/班次/日历（HR）、今日打卡与余额（员工）、异常处理和月报（HR/经理）。H5 只先做打卡、余额和补卡入口。

## 测试与退出条件

- 固定班、午休、跨夜、跨日、跨年、节假日和时区转换结果正确。
- 两个并发请假申请不能透支；拒绝、撤销、重复事件均能正确释放或保持账本。
- 重复打卡只保留原始记录并返回幂等结果；修改服务器时间、员工 ID 或部门参数无效。
- 经理只能查看本部门，HR 可维护规则，员工只能查看本人；导出遵守同一权限。
- 月报与明细可对账，迁移升级和流程回调失败有回滚测试。

## 实施记录（2026-10-03，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 5 节为准，与本文档草案的差异如下（已同步到 09 第 10 节）：

- 路径/权限对齐 05：打卡 `POST /api/v1/attendance/punch`（`/today`、`/records`）、规则 `GET/POST/PUT/DELETE /api/v1/attendance/groups`（`/all`、`/{id}`、`/{id}/members`）、`/shifts`（`/all`、`/{id}`）、`/calendar`（`/holidays`）、额度 `GET /api/v1/attendance/leaves/balance`（`/{userId}`、`POST /balance/grant`、`GET /leaves/ledger`）、日报 `GET /api/v1/attendance/days`（`/today`）、报表 `GET /api/v1/attendance/statistics/daily|monthly|export`；本文档草案的 `/punches`、`/leave/balances`、`/reports/*` 不再使用。权限串按 `hr:`/`wf:` 惯例取 `at:*`（`at:group:*`、`at:shift:*`、`at:calendar:*`、`at:leave:query|grant`、`at:report:list|export`），打卡与余额自助接口仅要求登录；权限点已补入 05 第 5 节。
- V6 迁移：`oa_attendance_group`、`oa_attendance_member`、`oa_shift`、`oa_calendar`、`oa_holiday`、`oa_punch_record`、`oa_attendance_day`、`oa_leave_type`、`oa_leave_balance`、`oa_leave_ledger`、`oa_overtime`、`oa_correction`（`oa_` 前缀按 V4 惯例统一 docs/04 的 `at_*` 草案）；加班/补卡承接单沿用模块 2 的 `oa_overtime_request`/`oa_correction_request`，`oa_overtime`/`oa_correction` 只存审批生效记录；`at_punch_type`/`at_day_type`/`at_work_status`/`at_leave_action` 字典、八假种与三条班次种子、`at:*` 菜单与角色授权。空库 V1→V6 与升级 V5→V6 各执行一次通过。
- 状态与快照：日报保存 `group_id`/`shift_id`/`rule_version` 快照，规则修改不重算历史日报；打卡时间为服务器接收时间（秒级精度），迟到、早退、缺卡是并存的计算结果，原始记录一律保留；跨夜班打卡归属班次开始日期；记录窗口与迟到/早退阈值分别配置，窗口外返回 `ATTENDANCE_WINDOW_INVALID`。
- 额度账本：GRANT/FREEZE/SETTLE/RELEASE/ADJUST 不可变流水（`event_key` 唯一，`balance_id + event_key` 去重），账本事件先落库再条件更新余额，同一事务内失败全部回滚；提交冻结、通过转已用、拒绝/撤销释放，条件更新防透支（`LEAVE_BALANCE_INSUFFICIENT`）；重复消费不重复扣减；不计额度假种（sick/personal 等）累计已用但不设上限，`availableMinutes` 返回 null。打卡与额度发放复用 M1 的 `oa_idempotency`/`IdempotencyGuard`（`Idempotency-Key` 必填，同键重放首次结果、同键异请求 409）。
- 流程回调（docs/12 暂缓项交付）：模块 2 新增 `FlowEventListener`/`FlowEventListenerRegistry` 与 `DurationCalculator`/`DurationCalculators` SPI，考勤注册 `AttendanceFlowListener`/`AttendanceDurationCalculator`，在编排事务内执行冻结/结算/释放、加班与补卡生效记录、日报重算；请假提交前校验假种、时间重叠（在途或已通过）与可用额度，补卡提交前校验每月上限（3 次）；请假时长按工作日历、班次、休息段服务端计算，未配置考勤组回退自然时长，不接受客户端时长。
- 权限矩阵落地并通过测试：员工（本人打卡/余额/账本/日报）、部门经理（本部门日报与月报、导出）、HR（规则维护、额度发放、他人余额、报表）、管理员；他人账本/余额查询需 `at:leave:query` 或 `at:report:list`，导出需 `at:report:export`。

退出条件证据：空库迁移与升级迁移各一次（V1→V6，本机 `docker compose` migrate 服务实测）；后端测试 36 个（班次时间计算固定班/午休/跨夜/弹性/宽限/窗口分离、权限矩阵、H2 集成：额度发放与幂等/防透支/冻结结算释放/事件重放不重复扣减/请假生命周期与撤销释放/重叠拒绝/补卡上限/加班补卡生效与日报重算/打卡迟到记录/窗口与无规则拒绝/幂等重放/缺卡标记/跨夜归属）；`smoke.mjs` 184 项全部通过（新增考勤契约：额度发放重放、打卡幂等、冻结 510 分钟（540−30 午休）与结算对账、账本 FREEZE/SETTLE、他人账本 403、日报/月报/日历）；`browser-smoke.mjs` 全部通过（含今日打卡/我的余额/考勤报表页面渲染）；前端 `build:prod` + `vue-tsc` 通过。

P0 未包含（P1/P2）：轮班与排班、外勤 GPS/照片、WiFi 与反作弊、考勤申诉、复杂假期政策与法定年假口径、跨年请假按工作日拆分年度额度、额度分批滚动过期、补卡 `punch_type=4` 展示语义、报表看板指标（模块 8 承接）。M3 验收报告要点已并入 [09](09-foundation.md) 第 10 节。

## P1 批次二 · 调休批次滚动过期（AT-06，V18，2026-10-05）

交付包 E 已完成（[09](09-foundation.md) 第 16 节汇总，表结构见 [04](04-database-design.md) 5.7）：

- V18__attendance_leave_batch：`oa_leave_batch`（批次：grant/frozen/used/expired、valid_from/expire_date、event_key 唯一、status 1有效/2已过期/3已用尽）+ `oa_leave_batch_allocation`（FREEZE/SETTLE/RELEASE/EXPIRE 分摊）+ 历史余额迁移为单批次。`oa_leave_balance` 汇总行与 `uk_balance_user_year_type` 不动，账本 `oa_leave_ledger` 仍是唯一流水源（批次经 allocation 关联）。
- 批次管理假种=调休（compensatory）+年假（annual）（docs/09 第 16 节决策点 5）；发放即建批次，`BalanceGrantBo` 增 `validFrom`（默认当天）/`expireDate`（调休默认 +3 个月滚动、年假默认当年 12-31），截止日必须晚于起始日；回收（负发放）按 LIFO 缩减批次 grant。
- 消耗规则 FIFO 先过期先扣（expire_date ASC，批次条件更新防透支，双批次并发冻结不透支）；结算/释放按冻结分配回原批次（撤销释放回原批次；历史在途冻结无分配时按冻结 FIFO 兜底）；不变式 SUM(批次剩余)==余额可用（对账测试覆盖）。
- 过期执行：`POST /api/v1/attendance/leaves/expire-scan`（`at:leave:grant`，对齐 timeout-scan 先例）+ `LeaveBatchExpiryDispatcher` 小时级自愈触发（复用 C 交付包调度骨架，幂等漏扫自动补）。过期清零可用并写 ADJUST 流水（`EXPIRE:{batchId}` 幂等，重复 scan 不重复减）；冻结过期保护至结算（结算仍成功）；过期批次释放归零（`expired_minutes` 归集 + 同步回收总额 + `RELEASE-WO:` ADJUST 流水）。
- 前端：余额页"额度批次"区块（批次号/假种/额度/冻结/已用/已过期/剩余/有效期至/状态 + 过期扫描按钮）、发放弹窗有效起始日/截止日字段。

边界（本包未做）：跨年请假拆分（独立 P1 项）；仅调休/年假按批次，其余计额度假种沿用年度 `expire_date` 展示现状；批次月报/年报口径合并展示为后续报表增强。

退出条件证据：`agentoa-attendance` 53 个测试全绿（新增 `LeaveBatchH2Test` 10 项：默认有效期与非法区间 400、FIFO 跨批次消耗、双批次防透支回滚、冻结/结算/释放 allocation 对账、释放回原批次、过期清零幂等（重扫 0、EXPIRE 流水 1 条）、冻结保护至结算、过期释放归零（ADJUST 对账）、SUM(批次)==余额对账、回收 LIFO）；既有 `LeaveBalanceH2Test` 8 项零回归（年假走批次路径不变式成立）；全量 `mvn -pl ruoyi-admin -am verify` 与前端 `build:prod`+`vue-tsc` 通过；`smoke.mjs` 增 Module 3b（批次创建/发放重放不双建/他人批次 403/expire-scan 员工 403 与重扫 0）。

## 变更清单（2026-10-08，[23](23-module-plan-h5.md) H5-H2-04）

| 变更 | 说明 | 影响 |
|---|---|---|
| `PunchRecordVo` 增 `lng`/`lat`/`accuracyMeters`/`photoFileId` | 打卡记录视图补充定位与现场照片（`oa_punch_record` 既有列，AutoMapper 自动映射；`photoFileId` 按雪花 ID 序列化为字符串防前端精度丢失） | 移动端打卡记录可展示坐标/照片缩略（属主可见）；无 DDL、无写路径变更；回归 `PunchH2Test#recordsExposeLocationAndPhoto` |

