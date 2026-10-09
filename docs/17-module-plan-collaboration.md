# 模块 7：日程、会议与任务开发计划

## 目标与边界

实现单次日程、会议室预约和基础任务。P0 包括参与人、可见范围、会议室冲突、取消、任务负责人/截止日期/状态和通知；重复日程、签到、任务依赖、看板、外部日历和会议视频列为 P1/P2。

## 开发顺序

1. V10 迁移：`oa_calendar_event`、`oa_calendar_attendee`、`oa_meeting_room`、`oa_room_booking`、`oa_task`、`oa_task_member`、`oa_task_activity`。
2. 实现 UTC 时间段和值域校验；预约使用数据库条件唯一/锁定策略，不能只依靠 Redis 锁。
3. 实现日程可见范围：私有、参与人、部门、全员；参与人新增/移除写活动记录并发通知。
4. 实现会议室可用查询、预约、取消和冲突返回；取消释放预约但保留历史。
5. 实现任务状态 `TODO/IN_PROGRESS/BLOCKED/DONE/CANCELLED`、负责人、协作者、评论和活动记录。
6. 前端月/周/日视图、会议室预约、我的任务、任务详情和通知入口。

API：`GET/POST/PUT /api/v1/collaboration/events`、`/rooms`、`/bookings`、`/tasks`、`/tasks/{id}/status`、`/tasks/{id}/members`、`/tasks/{id}/activities`。

## 测试与退出条件

测试并发预约同一会议室、跨时区、取消竞态、私有日程越权、任务状态非法跳转、已完成任务修改、通知重复和列表分页。退出条件是预约冲突可复现且无重复成功记录，日程和任务可见性通过角色矩阵。

## 实施记录（2026-10-04，P0 完成）

P0 已交付并通过验证，接口以 [05](05-api-spec.md) 第 9 节（9.4 实现口径补充）为准，与本文档草案的差异如下（已同步到 [09](09-foundation.md) 第 14 节）：

- 路径/权限对齐 05：`/api/v1/calendar/events`（`GET` 范围查询 `?start=&end=&scope=self|organized`、`POST`、`PUT /{id}`、`DELETE /{id}`、`PUT /{id}/accept|reject`）、`/api/v1/calendar/rooms`（`/all`、`/{id}`）与 `/{id}/bookings`、`/api/v1/calendar/bookings/{id}/checkin|cancel`、`/api/v1/tasks`（`/board`、`/{id}`、`/{id}/status`、`/{id}/progress`、`/{id}/comments`、`/{id}/members`、`/{id}/activities`）；本文档草案的 `/api/v1/collaboration/*` 不再使用。权限串 `cl:event:add|edit|remove`、`cl:room:add|edit|remove`（HR）、`cl:room:book`、`cl:task:add|edit|remove`，对象级授权：日程修改/删除仅组织者、任务管理仅指派人/负责人、预约签到/取消仅预订人或日程组织者（超管旁路），无「管理范围」旁路（对齐 05 1.7「日程限组织者/参与人」）。
- V10 迁移：`oa_calendar_event`、`oa_calendar_attendee`、`oa_meeting_room`、`oa_room_booking`、`oa_task`、`oa_task_member`、`oa_task_activity`（docs/04 `cl_*` 草案按 V4 惯例统一 `oa_` 前缀）；`cl_event_type`/`cl_visibility`/`cl_room_status`/`cl_booking_status`/`cl_task_status`/`cl_task_priority` 字典、`cl:*` 菜单（2600–2619）与角色授权（HR 全按钮含会议室维护，其余业务角色自助按钮）。空库 V1→V10 与升级 V9→V10（本机 `docker compose` migrate 服务实测）各执行一次通过。
- 时间与并发：时间点 RFC 3339 带偏移（无偏移按 UTC 解释），存储 UTC，输出 RFC 3339 UTC；任务起止为 `yyyy-MM-dd`。预约必须携带 `Idempotency-Key`（同键重放返回首次结果、同键异请求 409 `IDEMPOTENCY_CONFLICT`），重叠检查用 `SELECT ... FOR UPDATE` 行锁（`room_id+status+start<end+end>start`，左闭右开，首尾相接不冲突）+ 状态条件更新，不依赖 Redis 锁；日程/任务修改提交 `lockVersion` 条件更新，失败 409 `VERSION_CONFLICT`；取消/签到/状态跳转条件更新保证只生效一次（重复 409 `CL_STATE_CONFLICT`）。状态转换端点不启用 `@RepeatSubmit`（避免500 掩盖契约 409），创建/编辑/删除保留防重复提交。
- 日程与预约：可见范围 1私有/2参与人/3部门/4全员，列表与详情在服务端按授权过滤（私有日程越权 404）；参与人新增/移除保留记录（移除标记 `REMOVED` 不删行）并通过 outbox 通知（确定性 event_id 去重，重复添加同一参与人不重复通知）；接受/拒绝仅限参与人并通知组织者。带会议室的日程在同事务锁定时段并生成一一对应预约（`uk_booking_event`，独立预约 `event_id` 为空）；日程取消/删除同事务释放预约（状态 4已释放）但保留历史；会议室删除前检查生效预约。
- 任务：状态机 `TODO/IN_PROGRESS/BLOCKED/DONE/CANCELLED`（docs/17 五态，docs/04 四态草案废弃），非法跳转 409 `CL_TASK_STATE_INVALID`，`DONE/CANCELLED` 终态后修改/删除/再变更 409 `CL_TASK_STATE_CONFLICT`（已完成任务修改测试），`DONE` 置完成时间与进度 100；协作者（`oa_task_member`）增删写活动记录并通知（重复添加去重）；评论与活动（`oa_task_activity`：创建/状态/进度/评论/成员）通知负责人/指派人/@提及去重；看板按五态分列。
- 前端 `views/calendar/{event,room}` + `views/task/mine` + `api/collaboration`：日视图（日期切换、邀请接受/拒绝、可见范围与参与人、会议室联动）、会议室预约（预约列表、签到/取消、维护管理）、我的任务（列表/看板、状态与进度、详情抽屉含活动流、评论、协作者与通知入口跳转消息中心）。
- 对前置模块无登记变更；通知复用 M0 outbox/M5 消息中心（`TODO/NOTICE` 类型，深链 `/collaboration/*`），未新增通知基础设施。
退出条件证据：空库迁移与升级迁移各一次（V1→V10 / V9→V10，本机 `docker compose` migrate 服务实测）；后端测试 25 个（权限与状态机纯函数、H2 集成：私有/部门/全员可见矩阵、参与人通知去重与移除留痕、锁版本冲突、会议室联动与重叠拒绝、删除释放预约、预约幂等重放/同键异请求 409/重叠409/首尾相接放行/取消竞态/签到一次/维护中拒绝、任务状态机与终态保护/越权 404/403/评论与成员通知去重/看板分列）；`smoke.mjs` 318 项全部通过（新增模块 7 契约：预约幂等与冲突、边界放行、签到/取消竞态与越权 403、事件房间联动与一约一议、私有日程越权 404、锁版本 409、删除释放预约、任务创建/进度/状态链/终态409/越权 404、评论活动、成员通知去重、看板五列）；`browser-smoke.mjs` 通过（含我的日程/会议室预约/我的任务页面渲染）；前端 `build:prod` + `vue-tsc` 通过。

P0 未包含（P1/P2）：重复日程（`repeat_rule` 列已留位）、任务依赖与子任务交互（`parent_id` 列已留位）、会议室扫码签到（签到接口已交付，扫码为前端形态）、周/月视图与外部日历订阅、会议视频、任务看板拖拽、@提及解析为 MENTION 消息（字典已留位）、跨时区值班场景的服务端时区选择器（统一按 UTC 存储/偏移输入）。
