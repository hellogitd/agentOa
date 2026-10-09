# 模块 5：公告通知开发计划

## 目标与边界

完善 M0 已有 outbox、站内信和 WebSocket，形成统一通知中心。P0 包括公告发布、受众、撤回、站内信、已读/未读、补拉和实时推送；邮件、短信、企业微信、定时推送和复杂订阅列为 P1/P2。

## 开发顺序

1. V8 迁移：`oa_announcement`、`oa_announcement_audience`、`oa_announcement_read`、`oa_notification_preference`；复用 `nc_message/sys_outbox`，补充事件类型和失败重投字段。
2. 实现公告生命周期 `DRAFT/PUBLISHED/RECALLED/ARCHIVED`，发布时冻结标题、正文、附件和受众快照。
3. 实现受众解析：全员、部门、角色、指定用户；发布后组织变更不追溯改变已发布受众。
4. 实现站内信统一适配：业务事件写 outbox，事务提交后落库，再推 WebSocket；客户端断线通过分页接口补拉。
5. 实现已读、全部已读、撤回和管理查询；撤回不删除审计或已读记录。
6. 前端工作台消息抽屉、公告列表、公告详情、发布编辑和撤回确认；WebSocket 只做提醒，数据以接口为准。

API：`GET/POST/PUT /api/v1/notices`、`POST /notices/{id}/publish`、`POST /notices/{id}/recall`、`GET /notices`、`GET /messages`、`GET /messages/unread-count`、`PUT /messages/{id}/read`、`PUT /messages/read-all`。

## 权限与业务规则

公告管理员只能发布授权范围；普通员工只能看到受众包含自己的公告；编辑已发布内容必须创建新版本或拒绝修改。达到 5 次失败的事件必须可查询、可人工重投并留下审计。

## 测试与退出条件

测试受众快照、重复发布、撤回竞态、outbox 重试、消息去重、断线补拉、WebSocket 票据失效和敏感内容不进日志。退出条件是公告受众和撤回权限通过角色矩阵，消息在实时推送、断线补拉和重复消费场景下均不丢失、不重复，失败事件可人工重投且有审计记录。

## 实施记录（2026-10-04，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 7 节为准，与本文档草案的差异如下（已同步到 09 第 12 节）：

- 路径/权限对齐 05：`/api/v1/notice`（`GET /list`、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}`、`POST /{id}/publish`、`PUT /{id}/revoke`、`GET /{id}/read-status`、`PUT /{id}/read`）与 `/api/v1/messages`（`GET`、`/unread-count`、`PUT /{id}/read`、`PUT /read-all`、`DELETE /{id}`）；本文档草案的 `/notices`、`POST /{id}/recall` 不再使用。权限串取 `nt:notice:add|edit|remove|recall|read` 与消息事件 `nt:outbox:list|redeliver`；公告浏览与消息自助仅需登录。补充端点：`GET/PUT /api/v1/notice/preferences`（通知偏好）、`GET /api/v1/messages/outbox`（`?eventStatus=` 失败事件查询）与 `POST /api/v1/messages/outbox/{id}/redeliver`（人工重投）。
- V8 迁移：`oa_announcement`、`oa_announcement_audience`、`oa_announcement_read`、`oa_notification_preference`（docs/04 的 `nc_notice*` 草案按 V4 惯例统一 `oa_` 前缀）；复用 `nc_message`/`sys_outbox` 并补充列（`nc_message.biz_type/biz_id/del_flag`、`sys_outbox.redeliver_count/redelivered_by/redelivered_time`）；`nc_notice_type`/`nc_scope_type`/`nc_msg_type`/`nc_notice_status` 字典、`nt:*` 菜单（2400–2416）与角色授权（HR 发布与重投，全员公告/消息中心）。空库 V1→V8 与升级 V7→V8 各执行一次通过。
- 公告生命周期 `DRAFT/PUBLISHED/RECALLED/ARCHIVED`：发布时冻结标题、正文、附件并物化受众快照（全员/部门/角色/指定人解析，发布后组织变更不追溯）；已发布内容拒绝修改（`NT_STATE_CONFLICT`，需新建公告，重新发布生成新 ID）；撤回保留受众与已读记录（审计不删），撤回后原接收人不可见；重复发布 409，发布幂等键重放返回首次结果。
- 站内信统一适配：公告发布在业务事务内按受众写 `sys_outbox`（`event_type=NOTICE`，`event_id=NOTICE-{noticeId}-{userId}` 唯一去重），事务提交后由底座投递器落 `nc_message` 并推 WebSocket；客户端断线经 `GET /api/v1/messages` 分页补拉；`uk_message_event_receiver` 保证重复消费不重复；未读统计返回 `total/todo/notice/mention/system` 分桶。通知偏好（`oa_notification_preference`）仅抑制实时提醒，消息仍持久化可补拉，未配置默认提醒。
- 已读与统计：`PUT /notice/{id}/read` 幂等（唯一键），`read_count` 只累计一次；`GET /notice/{id}/read-status` 分母为受众快照人数（管理者/发布人可见）。消息删除为本人副本软删除（`del_flag`），保留去重键与审计。
- 失败事件运维（docs/15 退出条件）：`GET /messages/outbox?eventStatus=FAILED` 可查询达 5 次失败事件（`nt:outbox:list`），`POST .../{id}/redeliver` 人工重投（`nt:outbox:redeliver`，仅 FAILED 可重投），重投重置退避并记录 `redeliver_count/redelivered_by/redelivered_time` + `@Log` 操作日志双审计。
- 对前置模块的登记变更（docs/10 变更清单）：底座 `MessageController` 增补类型/未读过滤、未读分桶、`read-all`、软删除；`OutboxDispatcher` 落库时写 `biz_type/biz_id`（消息深链），投递顺序、锁租约与 5 次退避机制不变。
- 前端 `views/notice/{announcement,message}` + `api/notice`：公告列表/详情/发布/修改/撤回/删除/已读统计，消息中心（未读筛选、类型筛选、已读/全部已读/删除、未读徽标）与消息事件查询/重投面板；WebSocket 只做提醒，数据以接口为准。

退出条件证据：空库迁移与升级迁移各一次（V1→V8，本机 `docker compose` migrate 服务实测）；后端测试 13 个（权限矩阵、H2 集成：受众快照（全员/部门/角色/指定人）、发布幂等与重复发布 409、已发布冻结与撤回保留审计、已读幂等与统计分母、陌生人 404、偏好抑制提醒不丢消息、outbox 失败查询/重投审计/非失败 409）；`smoke.mjs` 240+ 项全部通过（新增 M5 契约：草稿对非受众不可见、发布快照与幂等重放、二次发布/发布后修改 409、outbox 消息投递补拉、未读分桶、已读/全部已读、已读统计、撤回后受众不可见、消息事件越权 403 与消息删除）；`browser-smoke.mjs` 通过（含公告管理/消息中心页面渲染）；前端 `build:prod` + `vue-tsc` 通过。

P0 未包含（P1/P2）：邮件/短信/企业微信渠道、定时与周期推送、复杂订阅与免打扰时段、公告富文本编辑器与附件上传 UI（数据模型已留 `attachments`）、撤回后消息条目的联动标记、MENTION 提及解析（字典已留位）。M5 验收报告要点已并入 [09](09-foundation.md) 第 12 节。

## P1 批次二 · 通知模板与定时推送（NC-04，V17，2026-10-05）

交付包 C 已完成（[09](09-foundation.md) 第 16 节汇总）：

- V17__notice_p1：`oa_notice_template`/`oa_scheduled_push`/`oa_scheduled_push_run` 三表（见 [04](04-database-design.md) 7.4）+ 内置模板种子（入职欢迎 `onboarding_welcome`、生日祝福 `birthday_greeting`、考勤异常提醒 `attendance_anomaly`）+ 字典 `nc_push_type`/`nc_schedule_type`/`nc_push_status` + 菜单 2420–2429（`nt:template:list|add|edit|remove|send`、`nt:schedule:list|add|edit|remove|run`，仅角色 20 HR 授权，员工不可）。
- 模板渲染（`NoticeTemplateRenderer`）：`{key}` 白名单占位符替换（对齐 M0 禁表达式原则，不引 FreeMarker/SpEL/脚本）；占位符必须在 `vars_json` 声明内（保存时 400），渲染时缺变量/未声明变量 400。模板发送 `POST /api/v1/notice/templates/{code}/send`（受众四类 + 变量 → outbox，受通知偏好抑制）。
- 定时推送（`ScheduledPushDispatcher` + `ScheduledPushServiceImpl`）：自研 `@Scheduled(fixedDelay=10s)` + DB 租约（`FOR UPDATE SKIP LOCKED`，与 `OutboxDispatcher` 同款，不引入 SnailJob）；`oa_scheduled_push_run.event_key=SCHED-{pushId}-{slot}` 唯一去重，到点执行一次且重扫不重复。定时公告到点调 `AnnouncementService.publish`（受众物化+outbox 同事务，与手动发布一致）；模板推送按受众渲染写 outbox；执行失败落 FAILED 执行记录并推进 slot（周期任务漏发合并、不回放历史 slot），outbox 投递失败仍走既有 FAILED 人工重投通道。手动兜底 `POST /api/v1/notice/push/run`（对齐 timeout-scan 先例）。cron 受限子集 5 字段（`分 时 日 月 周`，`*`/`*/n`/数字/`a-b`/逗号列表；日+周同限按标准 cron 或语义；名称/L/W/#/? 拒绝）。暂停不执行、恢复重排。
- 前端 `views/notice/template`（模板 CRUD、变量声明、按受众发送）、`views/notice/schedule`（任务 CRUD、暂停/恢复、执行历史、手动执行到期）+ `api/notice` 扩展。
- 边界（本包未做）：站内信单渠道（邮件/短信/企业微信 P2）；模板仅 `{var}` 占位符（无条件/循环/格式化函数）；cron 不支持名称与 L/W/# 等高级指令；"编辑整个系列/批量任务编排"不在范围；公告富文本/附件沿用 P0 状态。

退出条件证据：`agentoa-notice` 35 个测试全绿（新增 `NoticeTemplateH2Test` 5 项：占位声明校验/编码冲突/渲染发送/缺变量与未声明变量拒绝/偏好与空受众；`ScheduledPushH2Test` 6 项：到点一次+重扫不重复/暂停恢复/定时公告受众与手动一致/cron 滚动与漏发合并/失败记录不重试/调度校验；`NoticeTemplateRendererTest` 6 项、`PushCronTest` 5 项）；全量 `mvn -pl ruoyi-admin -am verify` 通过；前端 `build:prod` + `vue-tsc` 通过；`smoke.mjs` 新增 Module 5b（模板发送送达、缺变量 400、定时到点触发、执行历史 1 条、重扫 0、员工 403 权限矩阵）。
