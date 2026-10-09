# 05 · API 接口规范

> 版本：v1.1（接口草案，尚无实现或 OpenAPI 文件）
> 更新日期：2026-10-02
> 实施状态（2026-10-02）：M0 基础工程已落地，实际版本、命令、接口与验证见 [09 · 底座使用与验收](09-foundation.md)。本文尚未标注实现的业务能力仍为设计。
> 协议：RESTful over HTTPS
> 认证：Sa-Token 不透明 Bearer 会话令牌 + Redis
> API 版本：`/api/v1`

---

## 1. 通用约定

### 1.1 URL 规范

```
/api/v1/{module}/{resource}
/api/v1/{module}/{resource}/{id}
/api/v1/{module}/{resource}/{id}/{action}
```

**示例**：
```
GET    /api/v1/hr/employees               # 员工列表
POST   /api/v1/hr/employees               # 新增员工
GET    /api/v1/hr/employees/{id}          # 员工详情
PUT    /api/v1/hr/employees/{id}          # 更新员工
DELETE /api/v1/hr/employees/{id}          # 删除员工
POST   /api/v1/wf/instances/start         # 发起流程
POST   /api/v1/wf/tasks/{id}/complete     # 完成任务
```

### 1.2 请求头

| Header | 必须 | 示例 |
|---|---|---|
| `Authorization` | 除登录等公开入口外必填 | `Bearer {token}` |
| `Content-Type` | 有请求体时必填 | JSON 用 application/json，上传用 multipart/form-data |
| `Idempotency-Key` | 流程提交、审批、打卡、付款等写操作必填 | UUID，最长 64 字符 |
| `X-Request-Id` | 可选 | `uuid`，用于链路追踪 |
| `Accept-Language` | 可选 | `zh-CN` / `en-US` |

### 1.3 统一响应格式

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {},
  "timestamp": 1790899200000,
  "requestId": "req-example-001"
}
```

**字段说明**：
| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 状态码（200 成功 / 400 参数 / 401 未授权 / 403 无权限 / 500 服务异常）|
| `msg` | string | 提示信息 |
| `data` | any | 业务数据（可为对象、数组、null）|
| `timestamp` | long | 响应时间戳（毫秒）|
| `requestId` | string | 请求追踪 ID，由服务器生成或校验后接收 |

成功接口默认 HTTP 200；HTTP 错误状态与 code 一致，不用 HTTP 200 包装失败。文件下载直接返回字节流和正确 Content-Type/Content-Disposition，不包 JSON。下文业务示例可省略 msg/timestamp/requestId，实际 JSON 响应均须包含这些字段。

### 1.4 分页请求

```
GET /api/v1/hr/employees?pageNum=1&pageSize=20&deptId=1&status=1
```

**Query 参数**：
| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `pageNum` | int | 1 | 页码 |
| `pageSize` | int | 20 | 每页数量（最大 100）|
| `orderBy` | string | - | 排序字段 |
| `orderDirection` | string | asc | 排序方向 |

**分页响应**：
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "pages": 5
  }
}
```

### 1.5 错误响应

```json
{
  "code": 400,
  "msg": "参数校验失败：手机号格式不正确",
  "data": null,
  "timestamp": 1790899200000
}
```

**标准错误码**：

| 错误码 | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（唯一约束）|
| 429 | 请求过于频繁 |
| 500 | 服务异常 |

业务错误另用字符串 errorCode，不与 HTTP code 混用：

| errorCode | HTTP | 含义 |
|---|---|---|
| AUTH_INVALID | 401 | 凭据无效；不暴露账号是否存在 |
| AUTH_LOCKED | 429 | 登录限流/暂时锁定 |
| WF_NOT_FOUND | 404 | 流程不存在或对当前用户不可见 |
| WF_FORBIDDEN | 403 | 无任务办理权限 |
| WF_STATE_CONFLICT | 409 | 任务/流程状态已改变 |
| LEAVE_BALANCE_INSUFFICIENT | 409 | 可用额度不足 |
| INVOICE_OCCUPIED | 409 | 发票已被其他报销占用 |
| ATTENDANCE_WINDOW_INVALID | 400 | 不在可记录窗口 |
| ATTENDANCE_LOCATION_INVALID | 400 | 定位不满足考勤规则 |
| FILE_TYPE_UNSUPPORTED | 400 | 文件类型不支持 |
| VERSION_CONFLICT | 409 | 业务版本已改变 |
| IDEMPOTENCY_CONFLICT | 409 | 同一幂等键对应不同请求或仍在处理中 |

### 1.6 类型、时间与校验

- 所有主键、外键和引擎 ID 均用 JSON 字符串，避免 JavaScript BIGINT 精度损失；状态/计数/版本保持整数。
- 金额为两位小数字符串（如 "2500.00"），首版 currency 固定 CNY；服务端 DECIMAL 计算并核对明细总额。
- 时间点使用 RFC 3339 且带偏移（如 2026-10-02T09:00:00+08:00）；纯业务日期使用 yyyy-MM-dd。数据库存 UTC，报表区间左闭右开。
- 请假额度与时长使用整数分钟，不接受客户端时长作为最终值。查询仅支持白名单字段、排序方向与范围，禁止把 orderBy 拼进 SQL。
- 未知/只读业务字段拒绝写入；申请人、审批人、状态、总额、部门快照由服务器确定。分页最大 100，超限返回 400。

### 1.7 幂等、并发与对象权限

流程发起、业务提交、审批操作、打卡、付款、额度发放、预约和公告发布必须提供 Idempotency-Key。唯一范围为当前用户 + 方法/具体资源路径 + 键；服务端保存请求摘要与结果至少 24h。同键同请求返回首次已提交结果；同键不同请求返回 409，处理中返回 409 并提示稍后重试。失败回滚不能留下“业务成功”的幂等结果。

成功记录与业务变更同事务保存，过期后仍使用业务唯一约束防重复；审批/付款不能只依赖 Redis 短锁。可修改单据提交 lockVersion，条件更新失败返回 VERSION_CONFLICT；文档编辑提交 baseVersion。返回原结果前仍需检查当前会话和对象访问权限。

所有“登录用户”仅代表认证前提。流程详情/图/历史仅发起人、实际参与人或具备明确管理范围的人员可读；任务办理还需核对引擎当前办理人。员工、考勤、报销默认本人范围，主管/HR/财务权限分别授权；公告限接收人或发布管理者；消息只能操作接收人自己的记录；知识库按空间角色；日程限组织者/参与人；报表与导出复用同样的数据过滤。无显式权限列的接口不等于公开接口。

### 1.8 范围与底座适配

接口目录同时列出规划中的 P1/P2。交付范围以需求第 1.5 节为准，未交付能力不得展示成可用入口。预算、代理、退回/加签、点赞/评论、扫码签到和看板等在对应阶段才启用。底座原生接口不保证具有本文路径，M0 需建立适配层并生成 OpenAPI 契约，不能只修改前缀就宣称兼容。

---

## 2. 认证接口

### 2.1 登录

```
POST /api/v1/auth/login
```

**请求**：
```json
{
  "username": "admin",
  "password": "example-only-password",
  "captchaCode": "1234",
  "captchaUuid": "uuid-xxx"
}
```

**响应**：
```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "opaque-session-token-example",
    "expiresIn": 7200,
    "tokenType": "Bearer",
    "user": {
      "userId": "1",
      "username": "admin",
      "nickname": "超级管理员",
      "avatar": "https://...",
      "deptId": "1",
      "deptName": "总部",
      "roles": ["admin"],
      "permissions": ["system:user:add", "..."]
    }
  }
}
```

### 2.2 会话有效期

绝对有效期 7200 秒，空闲超时 1800 秒，任一到期返回 401 并重新登录。P0 不提供 refreshToken 或刷新接口。密码经 HTTPS 提交原值，在服务器使用 BCrypt 验证，禁止前端预先 MD5 后当作密码协议。

### 2.3 登出

```
POST /api/v1/auth/logout
```

### 2.4 获取当前用户信息

```
GET /api/v1/auth/profile
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "userId": "1",
    "username": "admin",
    "nickname": "超级管理员",
    "avatar": "https://...",
    "email": "admin@example.com",
    "phone": "138****8888",
    "deptId": "1",
    "deptName": "总部",
    "postId": "1",
    "postName": "总经理",
    "roles": ["admin"],
    "permissions": ["*:*:*"],
    "lastLoginTime": "2026-10-02T10:00:00+08:00"
  }
}
```

### 2.5 修改密码

```
PUT /api/v1/auth/password
```

```json
{
  "oldPassword": "old-example-password",
  "newPassword": "new-example-password"
}
```

---

## 3. 组织人事（HR）

### 3.1 部门管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/hr/departments/tree` | 部门树 | `hr:dept:list` |
| POST | `/api/v1/hr/departments` | 新增部门 | `hr:dept:add` |
| PUT | `/api/v1/hr/departments/{id}` | 更新部门 | `hr:dept:edit` |
| DELETE | `/api/v1/hr/departments/{id}` | 删除部门 | `hr:dept:remove` |
| PUT | `/api/v1/hr/departments/{id}/status` | 启停用 | `hr:dept:edit` |

**部门树响应**：
```json
{
  "code": 200,
  "data": [
    {
      "id": "1",
      "name": "总部",
      "parentId": "0",
      "sort": 1,
      "status": 1,
      "children": [
        {
          "id": "2",
          "name": "技术部",
          "parentId": "1",
          "children": []
        }
      ]
    }
  ]
}
```

### 3.2 岗位管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/hr/posts` | 岗位列表 | `hr:post:list` |
| POST | `/api/v1/hr/posts` | 新增 | `hr:post:add` |
| PUT | `/api/v1/hr/posts/{id}` | 更新 | `hr:post:edit` |
| DELETE | `/api/v1/hr/posts/{id}` | 删除 | `hr:post:remove` |

### 3.3 员工管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/hr/employees` | 员工列表（分页）| `hr:employee:list` |
| GET | `/api/v1/hr/employees/{id}` | 员工详情 | `hr:employee:query` |
| POST | `/api/v1/hr/employees` | 新增员工 | `hr:employee:add` |
| PUT | `/api/v1/hr/employees/{id}` | 更新员工 | `hr:employee:edit` |
| DELETE | `/api/v1/hr/employees/{id}` | 删除员工 | `hr:employee:remove` |
| POST | `/api/v1/hr/employees/import` | Excel 导入 | `hr:employee:import` |
| GET | `/api/v1/hr/employees/export` | Excel 导出 | `hr:employee:export` |
| PUT | `/api/v1/hr/employees/{id}/status` | 仅修正允许的初始状态；在职生命周期由审批驱动 | `hr:employee:edit` |
| GET | `/api/v1/hr/employees/{id}/changes` | 异动历史 | `hr:employee:query` |

**新增员工请求**：
```json
{
  "name": "张三",
  "gender": 1,
  "birthDate": "1995-05-15",
  "idCardNo": "110101199505151234",
  "phone": "13800138000",
  "email": "zhangsan@example.com",
  "deptId": "2",
  "postId": "5",
  "positionLevel": "P6",
  "directLeaderId": "1",
  "employeeType": 1,
  "entryDate": "2026-10-01",
  "contractStartDate": "2026-10-01",
  "contractEndDate": "2029-09-30",
  "education": [
    {
      "school": "北京大学",
      "major": "计算机",
      "education": "本科",
      "degree": "学士",
      "startDate": "2013-09-01",
      "endDate": "2017-07-01"
    }
  ]
}
```

### 3.4 入职办理

```
POST /api/v1/hr/onboard
```

```json
{
  "name": "李四",
  "phone": "13800138001",
  "deptId": "2",
  "postId": "5",
  "entryDate": "2026-10-15",
  "employeeType": 1,
  "directLeaderId": "1"
}
```

**响应**：返回员工 ID、账号 ID、工号和激活状态；不返回可长期使用的初始密码。一次性激活材料经受控渠道交付，首次激活后失效。

### 3.5 转正

```
POST /api/v1/hr/regularize
```

保存转正申请草稿，返回申请 id/lockVersion；`POST /api/v1/hr/lifecycle-requests/{id}/submit` 经同一业务流程服务提交，不能直接 PUT 员工转正状态。`GET /api/v1/hr/lifecycle-requests/{id}` 返回本人或 HR 授权范围的申请。

### 3.6 调岗调薪

```
POST /api/v1/hr/changes
```

```json
{
  "employeeId": "10",
  "changeType": 3,
  "effectiveDate": "2026-11-01",
  "newDeptId": "3",
  "newPostId": "6",
  "newSalary": "18000.00",
  "reason": "晋升"
}
```

### 3.7 离职办理

```
POST /api/v1/hr/offboard
```

保存离职申请草稿，复用 lifecycle-requests 提交与详情接口；办理人审核交接清单后按生效日期冻结账号与会话，保留历史数据。此处接口是建议适配路径，需在 M1 OpenAPI 中补齐字段、校验与权限。

---

## 4. 流程工作流（WF）

### 4.1 流程定义

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/wf/definitions` | 流程定义列表 | `wf:definition:list` |
| GET | `/api/v1/wf/definitions/{id}` | 定义详情 | `wf:definition:query` |
| POST | `/api/v1/wf/definitions` | 发布新版本，绑定确切表单版本 | `wf:definition:add` |
| PUT | `/api/v1/wf/definitions/{id}` | 仅更新展示元数据，不改已发布 XML/表单 | `wf:definition:edit` |
| PUT | `/api/v1/wf/definitions/{id}/status` | 启停用 | `wf:definition:edit` |
| GET | `/api/v1/wf/definitions/{id}/xml` | BPMN XML | `wf:definition:query` |
| POST | `/api/v1/wf/definitions/import` | 导入 BPMN | `wf:definition:import` |
| GET | `/api/v1/wf/definitions/{id}/diagram` | 流程图 PNG | `wf:definition:query` |

### 4.2 表单管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/wf/forms` | 表单列表 | `wf:form:list` |
| GET | `/api/v1/wf/forms/{formKey}` | 表单 Schema | `wf:form:query` |
| POST | `/api/v1/wf/forms` | 新增表单 | `wf:form:add` |
| PUT | `/api/v1/wf/forms/{id}` | 仅展示元数据；结构变化须发布新版本 | `wf:form:edit` |
| GET | `/api/v1/wf/forms/{formKey}/preview` | 表单预览 | `wf:form:query` |

**表单 Schema 响应**：
```json
{
  "code": 200,
  "data": {
    "formKey": "leave",
    "formName": "请假申请",
    "version": 1,
    "schema": {
      "schemaVersion": 1,
      "fields": [
        {
          "key": "leaveType",
          "label": "请假类型",
          "type": "select",
          "required": true,
          "options": [
            {"label": "年假", "value": "annual"},
            {"label": "事假", "value": "personal"},
            {"label": "病假", "value": "sick"}
          ]
        }
      ]
    }
  }
}
```

### 4.3 流程实例

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/v1/wf/instances/start` | 发起流程 | 登录用户 |
| GET | `/api/v1/wf/instances` | 流程列表（我发起/我的待办/全部）| `wf:instance:list` |
| GET | `/api/v1/wf/instances/{id}` | 流程详情 | 登录用户 |
| PUT | `/api/v1/wf/instances/{id}/revoke` | 撤回流程 | 发起人 |
| PUT | `/api/v1/wf/instances/{id}/suspend` | 挂起 | `wf:instance:suspend` |
| PUT | `/api/v1/wf/instances/{id}/resume` | 恢复 | `wf:instance:resume` |
| PUT | `/api/v1/wf/instances/{id}/terminate` | 强制终止 | `wf:instance:terminate` |
| GET | `/api/v1/wf/instances/{id}/diagram` | 流程图（高亮）| 登录用户 |
| GET | `/api/v1/wf/instances/{id}/history` | 审批历史 | 登录用户 |

**发起流程请求**：
```json
{
  "processKey": "leave",
  "businessType": "leave",
  "businessId": "301",
  "lockVersion": 0,
  "title": "张三的请假申请",
  "priority": 0,
  "formData": {
    "leaveType": "annual",
    "startTime": "2026-10-12T09:00:00+08:00",
    "endTime": "2026-10-12T18:00:00+08:00",
    "reason": "回家探亲"
  },
  "assigneeSelections": {
    "pickApprover": "5"
  }
}
```

**assigneeSelections（发起人自选审批人，可选）**：`{ nodeId: userId | userId[] }`。
- 仅对链上 `assigneeRule=USER_SELECT` 的节点生效；键必须是链上自选节点 ID，其他键直接 400（防变量注入）。
- `signMode=SINGLE` 传单个 `userId`；`COUNTERSIGN/EITHERSIGN` 传 `userId[]`（≥1 人）。
- userId 做存在性 + 启用校验；缺失/为空确定性报错 400 并整体回滚，不回退任意人。
- 业务承接单提交（`POST /api/v1/{module}/{biz}/{id}/submit`）与通用申请发起/launch 的请求体同样接受该字段。

**响应**：
```json
{
  "code": 200,
  "msg": "流程发起成功",
  "data": {
    "instanceId": "100",
    "processInstanceId": "abc-123-def",
    "businessKey": "LV20261002001",
    "currentTasks": [{
      "taskId": "200",
      "taskName": "主管审批",
      "assigneeId": "1",
      "assigneeName": "张经理"
    }]
  }
}
```

### 4.4 任务处理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/wf/tasks/todo` | 我的待办 | 登录用户 |
| GET | `/api/v1/wf/tasks/done` | 我的已办 | 登录用户 |
| GET | `/api/v1/wf/tasks/cc` | 抄送我的（P1） | 登录用户 |
| GET | `/api/v1/wf/tasks/{id}` | 任务详情 | 登录用户 |
| POST | `/api/v1/wf/tasks/{id}/complete` | 完成任务（同意/拒绝）| 办理人 |
| POST | `/api/v1/wf/tasks/{id}/return` | 退回 | 办理人 |
| POST | `/api/v1/wf/tasks/{id}/transfer` | 转办 | 办理人 |
| POST | `/api/v1/wf/tasks/{id}/addsign` | 加签 | 办理人 |
| POST | `/api/v1/wf/tasks/{id}/urge` | 催办 | 发起人 |

**完成任务请求**：
```json
{
  "action": "agree",
  "comment": "同意，请妥善安排工作",
  "lockVersion": 0
}
```

**action 取值**：complete 只接受 `agree` 或 `reject`；其他动作使用各自端点。客户端不得提交 approved、assignee 等引擎控制变量，服务端从动作和业务规则生成。唯一例外是发起接口的 `assigneeSelections`：仅链上 `USER_SELECT` 节点的选择结果可上送，其余引擎变量仍禁止。

**转办请求**：
```json
{
  "targetUserId": "5",
  "reason": "出差期间由李四代为处理"
}
```

**加签请求**：
```json
{
  "position": "before",
  "assigneeIds": ["5", "6"],
  "reason": "增加财务审核"
}
```

### 4.5 委托代理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/wf/delegates` | 委托列表 |
| POST | `/api/v1/wf/delegates` | 新增委托 |
| PUT | `/api/v1/wf/delegates/{id}` | 更新 |
| DELETE | `/api/v1/wf/delegates/{id}` | 删除 |

### 4.6 选人目录

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/wf/assignable-users` | 可选办理人检索 | 登录用户 |

`keyword` 按账号名/昵称模糊匹配，`pageNum`/`pageSize` 分页。只返回启用账号（`sys_user.status='0' AND del_flag='0'`）的最小身份字段，不暴露手机号/邮箱等敏感字段；不受 HR `data_scope` 收窄影响（普通员工发起自选/转办也要能选到同事）。

**响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      { "userId": "5", "name": "李四", "deptName": "研发部" }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 20,
    "pages": 1
  }
}
```

可发起流程（`GET /api/v1/wf/launchable/{id}`）同时返回 `selectableNodes`，供发起页渲染「选择审批人」行：

```json
{
  "selectableNodes": [
    { "nodeId": "pickApprover", "name": "选择审批人", "multiple": false }
  ]
}
```

---

## 5. 考勤（AT）

### 5.1 打卡

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/attendance/punch` | 打卡 |
| GET | `/api/v1/attendance/punch/today` | 今日打卡状态 |
| GET | `/api/v1/attendance/punch/records` | 打卡记录（分页）|

**打卡请求**：
```json
{
  "punchType": 1,
  "lng": 116.4074,
  "lat": 39.9042,
  "address": "北京市东城区xxx",
  "device": "iPhone 15",
  "accuracyMeters": 25
}
```

**响应**：
```json
{
  "code": 200,
  "msg": "打卡成功",
  "data": {
    "punchTime": "2026-10-02T09:05:23+08:00",
    "isLate": true,
    "lateMinutes": 5,
    "punchType": 1,
    "location": "北京市东城区xxx"
  }
}
```

### 5.2 考勤组与班次

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/attendance/groups` | 考勤组列表 |
| POST | `/api/v1/attendance/groups` | 新增 |
| PUT | `/api/v1/attendance/groups/{id}` | 更新 |
| DELETE | `/api/v1/attendance/groups/{id}` | 删除 |
| GET | `/api/v1/attendance/shifts` | 班次列表 |
| POST | `/api/v1/attendance/shifts` | 新增 |
| PUT | `/api/v1/attendance/shifts/{id}` | 更新 |

### 5.3 假期额度

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/attendance/leaves/balance` | 我的余额 |
| GET | `/api/v1/attendance/leaves/balance/{userId}` | 某员工余额 |
| POST | `/api/v1/attendance/leaves/balance/grant` | 发放额度 |

**余额响应**：
```json
{
  "code": 200,
  "data": [
    {"leaveType": "annual", "typeName": "年假", "quotaLimited": true, "totalMinutes": 4800, "frozenMinutes": 480, "usedMinutes": 960, "availableMinutes": 3360},
    {"leaveType": "sick", "typeName": "病假", "quotaLimited": false, "usedMinutes": 960, "availableMinutes": null}
  ]
}
```

**额度批次（AT-06，P1 批次二 · 交付包 E，V18）**：调休/年假按批次滚动过期（FIFO 先过期先扣，冻结过期保护至结算，释放回原批次、过期批次释放归零）。`POST /balance/grant` 增可选 `validFrom`/`expireDate`（调休默认 +3 个月、年假默认当年 12-31；截止日必须晚于起始日，否则 400）。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/attendance/leaves/batches?userId=&year=&leaveType=` | 登录即可（本人）/`at:leave:query`（他人） | 批次列表（批次号、额度、冻结、已用、已过期、剩余、有效期、状态 1有效/2已过期/3已用尽） |
| POST | `/api/v1/attendance/leaves/expire-scan` | `at:leave:grant` | 批次过期扫描（幂等，`EXPIRE:{batchId}` 去重；调度器小时级自动触发，手动兜底） |

### 5.4 业务申请

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/attendance/leaves` | 保存请假草稿；返回 id、lockVersion、服务器计算时长 |
| PUT | `/api/v1/attendance/leaves/{id}` | 修改本人草稿/被拒或撤销后的草稿 |
| GET | `/api/v1/attendance/leaves/{id}` | 查看本人或授权范围内的申请 |
| POST | `/api/v1/attendance/leaves/{id}/submit` | 冻结额度并启动流程，要求幂等键和 lockVersion |
| POST | `/api/v1/attendance/overtimes` | 保存加班申请草稿 |
| POST | `/api/v1/attendance/overtimes/{id}/submit` | 提交加班审批 |
| POST | `/api/v1/attendance/corrections` | 保存补卡申请草稿 |
| POST | `/api/v1/attendance/corrections/{id}/submit` | 提交补卡审批 |

业务提交接口调用同一流程应用服务；通用 instances/start 也必须经对应业务适配器做额度/金额/状态校验，不能旁路启动一份无业务约束的 JSON 流程。提交请求只传业务版本和必要表单字段，服务器选择并返回确切发布定义/表单版本。客户端只能查看自己的打卡/余额，按人员查询或发放额度需要 HR 权限与数据范围。

### 5.5 考勤报表

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/attendance/statistics/daily` | 日报 |
| GET | `/api/v1/attendance/statistics/monthly` | 月报 |
| GET | `/api/v1/attendance/statistics/export` | 导出 Excel |

### 5.6 权限与实现口径（M3 交付补充）

第 5 节接口的权限串与补充端点（1.7 的“考勤默认本人范围”落地口径，[13](13-module-plan-attendance.md) 实施记录同步）：

| 权限串 | 覆盖接口 |
|---|---|
| 登录即可 | `POST /attendance/punch`、`GET /attendance/punch/today`、`GET /attendance/punch/records`（他人需 `at:leave:query`/`at:report:list`）、`GET /attendance/leaves/balance`、`GET /attendance/leaves/ledger`（他人同前）、`GET /attendance/days`、`GET /attendance/days/today` |
| `at:group:list/query/add/edit/remove` | `/attendance/groups`（含 `/{id}/members`） |
| `at:shift:list/query/add/edit/remove` | `/attendance/shifts` |
| `at:calendar:list` / `at:calendar:edit` | `/attendance/calendar`（`/calendar/holidays`） |
| `at:leave:query` | `GET /attendance/leaves/balance/{userId}` |
| `at:leave:grant` | `POST /attendance/leaves/balance/grant` |
| `at:report:list` / `at:report:export` | `/attendance/statistics/daily|monthly|export` |

补充端点：`GET/PUT /api/v1/attendance/calendar`（工作日历批量维护）、`GET/POST /api/v1/attendance/calendar/holidays`、`DELETE /api/v1/attendance/calendar/holidays/{id}`、`GET /api/v1/attendance/days`、`GET /api/v1/attendance/days/today`、`GET /api/v1/attendance/leaves/ledger`、`GET /api/v1/attendance/groups/{id}/members`（`POST`、`DELETE /{memberId}`）、`GET /api/v1/attendance/shifts/all`、`/groups/all`。

`POST /attendance/punch` 与 `POST /attendance/leaves/balance/grant` 必填 `Idempotency-Key`（1.7）；打卡时间取服务器接收时间，不接受客户端时间；请假时长由服务端按工作日历、班次与休息段计算。

---

## 6. 财务报销（FN）

### 6.1 报销单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/finance/reimburses` | 报销列表 |
| GET | `/api/v1/finance/reimburses/{id}` | 报销详情 |
| POST | `/api/v1/finance/reimburses` | 创建报销单 |
| PUT | `/api/v1/finance/reimburses/{id}` | 更新报销单 |
| DELETE | `/api/v1/finance/reimburses/{id}` | 删除 |
| POST | `/api/v1/finance/reimburses/{id}/submit` | 提交（发起流程）|
| POST | `/api/v1/finance/reimburses/{id}/pay` | 登记付款 |

**创建报销单**：
```json
{
  "reimburseType": "差旅报销",
  "totalAmount": "2500.00",
  "currency": "CNY",
  "payMethod": "转账",
  "remark": "出差上海的差旅费",
  "details": [
    {
      "expenseTypeId": "1",
      "occurDate": "2026-09-25",
      "amount": "1500.00",
      "invoiceId": "501",
      "description": "机票"
    },
    {
      "expenseTypeId": "2",
      "occurDate": "2026-09-25",
      "amount": "1000.00",
      "invoiceId": "502",
      "description": "酒店"
    }
  ]
}
```

### 6.2 费用类型

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/finance/expense-types` | 类型树 |
| POST | `/api/v1/finance/expense-types` | 新增 |
| PUT | `/api/v1/finance/expense-types/{id}` | 更新 |
| DELETE | `/api/v1/finance/expense-types/{id}` | 删除 |

### 6.3 预算

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/finance/budgets` | 预算列表 |
| POST | `/api/v1/finance/budgets` | 新增预算 |
| PUT | `/api/v1/finance/budgets/{id}` | 更新 |
| GET | `/api/v1/finance/budgets/{id}/usage` | 使用情况 |

### 6.4 付款

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/finance/payments` | 付款列表 |
| GET | `/api/v1/finance/payments/{id}` | 付款详情；按财务/出纳授权范围 |

付款唯一写入口为 `POST /api/v1/finance/reimburses/{id}/pay`。要求出纳权限、已通过/待付款状态、幂等键和 lockVersion。首版只记录一次人工全额付款，不向银行转账，不暴露任意修改付款状态的接口。

```json
{
  "lockVersion": 3,
  "amount": "2500.00",
  "payDate": "2026-10-15",
  "paymentMethod": "BANK_TRANSFER",
  "voucherNo": "EXAMPLE-20261015-001"
}
```

### 6.5 发票

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/finance/invoices` | 录入类型、代码、号码、金额、fileId；规范化去重 |
| GET | `/api/v1/finance/invoices/{id}` | 本人或财务授权范围详情 |

报销草稿可引用本人发票；提交时按固定 ID 顺序加锁并占用，同一发票不得分摊到多条明细或多单。拒绝/撤销释放占用，已付款保持占用。超过发票金额、总额与明细不符或重复占用返回 400/409。

### 6.6 权限与实现口径（M4 交付补充）

第 6 节接口的权限串与补充端点（docs/14 权限与安全落地口径，[14](14-module-plan-finance.md) 实施记录同步）：

| 权限串 | 覆盖接口 |
|---|---|
| 登录即可 | `POST/GET /finance/invoices`（本人）、`GET/POST/PUT/DELETE /finance/reimburses`（本人草稿与提交，模块 2 承接单） |
| `fn:expense-type:list/query/add/edit/remove` | `/finance/expense-types` |
| `fn:invoice:query` | 发票明文详情/图片下载与他人发票查询（发票明文与付款登记显式绑定 `finance`/`cashier` 角色，管理员不自动获得） |
| `fn:payment:add` | `POST /finance/reimburses/{id}/pay`（另需 `finance`/`cashier` 角色） |
| `fn:payment:list` / `fn:payment:query` | `GET /finance/payments`、`GET /finance/reimburses/pending` |
| `fn:report:list` / `fn:report:export` | `/finance/reports/expense|export` |

补充端点：`DELETE /api/v1/finance/reimburses/{id}`（草稿删除）、`GET /api/v1/finance/reimburses/pending`（待付款清单）、`GET /api/v1/finance/invoices/{id}/download`（发票图片，本人或财务/出纳）、`GET /api/v1/finance/invoices?ownerUserId=`。`POST /finance/reimburses/{id}/pay` 必填 `Idempotency-Key`（1.7）；付款金额以两位小数字符串传输、服务端以最小货币单位核对，必须等于已审批总额；明细金额与发票金额按同一口径比较。

---

## 7. 公告通知（NC）

### 7.1 公告

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/notice/list` | 公告列表 |
| GET | `/api/v1/notice/{id}` | 公告详情 |
| POST | `/api/v1/notice` | 创建公告草稿 |
| POST | `/api/v1/notice/{id}/publish` | 发布并生成接收人快照，要求幂等键 |
| PUT | `/api/v1/notice/{id}` | 更新公告 |
| DELETE | `/api/v1/notice/{id}` | 删除公告 |
| PUT | `/api/v1/notice/{id}/revoke` | 撤回 |
| GET | `/api/v1/notice/{id}/read-status` | 已读/未读列表 |
| PUT | `/api/v1/notice/{id}/read` | 标记已读 |

### 7.2 站内信 / 消息中心

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/messages` | 消息列表 |
| GET | `/api/v1/messages/unread-count` | 未读数 |
| PUT | `/api/v1/messages/{id}/read` | 标记已读 |
| PUT | `/api/v1/messages/read-all` | 全部已读 |
| DELETE | `/api/v1/messages/{id}` | 删除消息 |

**未读数响应**：
```json
{
  "code": 200,
  "data": {
    "total": 15,
    "todo": 8,
    "notice": 5,
    "mention": 2
  }
}
```

### 7.3 权限与实现口径（M5 交付补充）

第 7 节接口的权限串与补充端点（docs/15 权限规则落地口径，[15](15-module-plan-notice.md) 实施记录同步）：

| 权限串 | 覆盖接口 |
|---|---|
| 登录即可 | `GET /notice/list`、`GET /notice/{id}`（受众可见）、`PUT /notice/{id}/read`、`GET /messages`、`/unread-count`、`PUT /messages/{id}/read`、`/read-all`、`DELETE /messages/{id}` |
| `nt:notice:add` | `POST /notice`、`POST /notice/{id}/publish`（发布要求 `Idempotency-Key`） |
| `nt:notice:edit` / `nt:notice:remove` | `PUT /notice/{id}`（仅草稿）、`DELETE /notice/{id}`（仅草稿） |
| `nt:notice:recall` | `PUT /notice/{id}/revoke` |
| `nt:notice:read` | `GET /notice/{id}/read-status`（发布人始终可见） |
| `nt:outbox:list` / `nt:outbox:redeliver` | `GET /messages/outbox`、`POST /messages/outbox/{id}/redeliver` |

补充端点：`GET/PUT /api/v1/notice/preferences`（通知偏好，仅抑制实时提醒）、`GET /api/v1/messages/outbox?eventStatus=FAILED`（失败事件查询）、`POST /api/v1/messages/outbox/{id}/redeliver`（人工重投，仅 FAILED，记录重投审计）。`GET /messages` 支持 `type`（TODO/NOTICE/MENTION/SYSTEM）与 `unread` 过滤；未读数响应为 `{total, todo, notice, mention, system}` 分桶（system 为补充桶，`total` 含全部）。已发布内容拒绝修改，重新发布生成新公告 ID。

### 7.4 通知模板与定时推送（NC-04，P1 批次二 · 交付包 C，V17__notice_p1）

模板为受控 `{var}` 占位符替换（白名单声明、缺变量/未声明变量拒绝，不引入表达式引擎）；定时推送分"定时公告"（到点调 `POST /notice/{id}/publish`，同事务受众物化+outbox）与"模板推送"（按受众渲染写 outbox）。执行幂等键 `event_key=SCHED-{pushId}-{slot}`，重扫不重复发；失败落执行记录并推进 slot，outbox 失败仍走既有 FAILED 重投通道。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/notice/templates/list` | `nt:template:list` | 模板分页（`vars` 为变量声明列表） |
| GET | `/api/v1/notice/templates/{id}` | `nt:template:list` | 模板详情 |
| POST | `/api/v1/notice/templates` | `nt:template:add` | 新建（`templateCode` 唯一，占位符必须声明） |
| PUT | `/api/v1/notice/templates/{id}` | `nt:template:edit` | 修改 |
| DELETE | `/api/v1/notice/templates/{id}` | `nt:template:remove` | 删除 |
| POST | `/api/v1/notice/templates/{code}/send` | `nt:template:send` | 按受众渲染发送，body `{scopeType, scopeValues, vars}`，返回投递人数 |
| GET | `/api/v1/notice/push/list` | `nt:schedule:list` | 推送任务分页 |
| GET | `/api/v1/notice/push/{id}` | `nt:schedule:list` | 任务详情 |
| POST | `/api/v1/notice/push` | `nt:schedule:add` | 新建（类型1公告/类型2模板；单次 `runAt` 或周期 `cronExpr`） |
| PUT | `/api/v1/notice/push/{id}` | `nt:schedule:edit` | 修改（按新调度重算 `nextRunTime`） |
| DELETE | `/api/v1/notice/push/{id}` | `nt:schedule:remove` | 删除任务与执行历史 |
| PUT | `/api/v1/notice/push/{id}/pause` / `resume` | `nt:schedule:edit` | 暂停/恢复 |
| GET | `/api/v1/notice/push/{id}/runs` | `nt:schedule:list` | 执行历史（结果/投递人数/错误） |
| POST | `/api/v1/notice/push/run` | `nt:schedule:run` | 手动触发到期扫描（与调度器同一入口，幂等） |

cron 受限子集：5 字段 `分 时 日 月 周`（周 0/7=周日），支持 `*`、`*/n`、数字、`a-b`、逗号列表；日与周同时受限按标准 cron"或"语义；未知指令（名称/L/W/#/?）拒绝。周期任务漏发合并（补发当次后跳到 now 之后的下一 slot，不回放历史 slot）；单次任务执行后 `nextRunTime` 置空。受众沿用 `nc_scope_type` 四类；消息落站内信单渠道（模板 `msgType` 取 TODO/NOTICE/SYSTEM）。

---

## 8. 知识库（KB）

### 8.1 知识空间

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/kb/spaces` | 空间列表 |
| POST | `/api/v1/kb/spaces` | 新增 |
| PUT | `/api/v1/kb/spaces/{id}` | 更新 |
| DELETE | `/api/v1/kb/spaces/{id}` | 删除 |
| GET | `/api/v1/kb/spaces/{id}/members` | 空间成员 |
| POST | `/api/v1/kb/spaces/{id}/members` | 添加成员 |

### 8.2 目录

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/kb/spaces/{spaceId}/folders/tree` | 目录树 |
| POST | `/api/v1/kb/folders` | 新增目录 |
| PUT | `/api/v1/kb/folders/{id}` | 更新 |
| DELETE | `/api/v1/kb/folders/{id}` | 删除 |

### 8.3 文档

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/kb/documents` | 文档列表 |
| GET | `/api/v1/kb/documents/{id}` | 文档详情 |
| POST | `/api/v1/kb/documents` | 新增 |
| PUT | `/api/v1/kb/documents/{id}` | 更新（生成版本）|
| DELETE | `/api/v1/kb/documents/{id}` | 删除（到回收站）|
| GET | `/api/v1/kb/documents/{id}/versions` | 版本历史 |
| PUT | `/api/v1/kb/documents/{id}/draft` | 保存个人草稿，包含 baseVersion |
| POST | `/api/v1/kb/documents/{id}/restore` | 30 天内从回收站恢复，需空间编辑权限 |
| GET | `/api/v1/kb/documents/{id}/versions/{version}` | 某版本内容 |
| PUT | `/api/v1/kb/documents/{id}/rollback/{version}` | 回滚 |
| POST | `/api/v1/kb/documents/{id}/like` | 点赞 |
| POST | `/api/v1/kb/documents/{id}/comments` | 评论 |
| GET | `/api/v1/kb/search` | 全文搜索 |

**搜索请求**：
```
GET /api/v1/kb/search?keyword=项目管理&pageNum=1&pageSize=10
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "documentId": "10",
        "title": "项目管理最佳实践",
        "highlight": "...<em>项目管理</em>是核心能力...",
        "spaceName": "技术文档",
        "lastEditTime": "2026-09-20T14:30:00+08:00"
      }
    ],
    "total": 42
  }
}
```

### 8.4 文件柜

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/kb/files` | 文件列表 |
| POST | `/api/v1/kb/files/upload` | 上传文件（multipart）|
| GET | `/api/v1/kb/files/{id}/download` | 下载文件 |
| GET | `/api/v1/kb/files/{id}/preview` | 鉴权后代理图片/PDF 预览（P0）|
| DELETE | `/api/v1/kb/files/{id}` | 删除 |

**上传请求**：
```
POST /api/v1/kb/files/upload
Content-Type: multipart/form-data

FormData:
  file: (binary)
  spaceId: 1
  folderId: 3
```

### 8.5 权限与实现口径（M6 交付补充）

第 8 节的实现口径（docs/16 落地，[16](16-module-plan-knowledge.md) 实施记录同步）：实现采用 `/api/v1/knowledge/...` 前缀，8.1–8.4 的 `/api/v1/kb/...` 为草案路径、不再使用；目录以文档树（`parentId`）实现，无独立目录接口；`PUT /documents/{id}/draft`、`POST /documents/{id}/restore`、`GET /documents/{id}/versions/{version}`、`PUT /documents/{id}/rollback/{version}` 保留，另补充 `PUT /documents/{id}/publish`、`PUT /documents/{id}/archive`、`POST /files/{id}/restore`。

| 权限串 | 覆盖接口 |
|---|---|
| 登录即可 | `GET /knowledge/spaces`、`/spaces/{id}`、`/spaces/{id}/members`、`GET /knowledge/documents`、`/documents/{id}`、`/documents/{id}/versions`、`/versions/{version}`、`/draft`、`GET /knowledge/documents/search`、`GET /knowledge/files`、`GET /files/{id}/download`、`/files/{id}/preview`（全部以空间/文档授权在服务端过滤） |
| `kn:space:add` / `kn:space:edit` / `kn:space:remove` | `POST /knowledge/spaces`、`PUT /spaces/{id}`、`DELETE /spaces/{id}`（另需空间 OWNER） |
| `kn:member:add` / `kn:member:edit` / `kn:member:remove` | `POST /spaces/{id}/members`、`PUT /spaces/{id}/members/{userId}`、`DELETE /spaces/{id}/members/{userId}`（另需空间 OWNER） |
| `kn:doc:add` / `kn:doc:edit` / `kn:doc:remove` | `POST /knowledge/documents`、`PUT /documents/{id}`、`PUT /documents/{id}/rollback/{version}`、`PUT /documents/{id}/draft`、`PUT /documents/{id}/publish`、`PUT /documents/{id}/archive`、`DELETE /documents/{id}`（另需空间 EDITOR 及以上） |
| `kn:doc:restore` | `POST /documents/{id}/restore`、`POST /files/{id}/restore`（另需空间 EDITOR 及以上） |
| `kn:file:upload` / `kn:file:remove` | `POST /knowledge/files`、`POST /documents/{id}/files`、`DELETE /files/{id}`（另需空间 EDITOR 及以上） |

补充口径：文档更新必须携带 `baseVersion`，与当前版本不符返回 409 `VERSION_CONFLICT`；版本历史（`document_id+version` 唯一）只追加、不可覆盖，回滚生成新版本；删除进回收站 30 天内可恢复（逾期 409），回收站内容不出现在搜索与默认列表；文件单个 ≤ 20 MiB，仅接受 PDF/PNG/JPEG/TXT/MD 且扩展名/内容/MIME 三重校验，下载按空间/文档角色授权并计数留审计（`download_count/last_download_by/last_download_time`）；权限矩阵按 docs/02 KB-08：空间角色 `OWNER/EDITOR/COMMENTER/VIEWER` 与文档 ACL（USER/ROLE 主体）取较高者，默认无权限，未授权用户（含无显式授权的管理员）不可见、不可编辑、不可下载。

---

## 9. 日程/会议/任务（CL）

### 9.1 日程/会议

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/calendar/events` | 日程列表（按时间范围）|
| POST | `/api/v1/calendar/events` | 新增日程 |
| PUT | `/api/v1/calendar/events/{id}` | 更新 |
| DELETE | `/api/v1/calendar/events/{id}` | 删除 |
| PUT | `/api/v1/calendar/events/{id}/accept` | 接受邀请 |
| PUT | `/api/v1/calendar/events/{id}/reject` | 拒绝邀请 |

**列表查询**：
```
GET /api/v1/calendar/events?start=2026-10-01&end=2026-10-31&scope=self
```

### 9.2 会议室

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/calendar/rooms` | 会议室列表 |
| POST | `/api/v1/calendar/rooms` | 新增 |
| PUT | `/api/v1/calendar/rooms/{id}` | 更新 |
| DELETE | `/api/v1/calendar/rooms/{id}` | 删除 |
| GET | `/api/v1/calendar/rooms/{id}/bookings` | 预约列表 |
| POST | `/api/v1/calendar/rooms/{id}/bookings` | 预约 |
| PUT | `/api/v1/calendar/bookings/{id}/checkin` | 扫码签到 |
| PUT | `/api/v1/calendar/bookings/{id}/cancel` | 取消 |

### 9.3 任务

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tasks` | 任务列表 |
| POST | `/api/v1/tasks` | 新增 |
| GET | `/api/v1/tasks/{id}` | 详情 |
| PUT | `/api/v1/tasks/{id}` | 更新 |
| DELETE | `/api/v1/tasks/{id}` | 删除 |
| PUT | `/api/v1/tasks/{id}/status` | 更新状态 |
| PUT | `/api/v1/tasks/{id}/progress` | 更新进度 |
| POST | `/api/v1/tasks/{id}/comments` | 添加评论 |
| GET | `/api/v1/tasks/{id}/comments` | 评论列表 |
| GET | `/api/v1/tasks/board` | 看板视图 |

### 9.4 权限与实现口径（M7 实施补充）

- 本文档 9.1–9.3 全量实现；[17](17-module-plan-collaboration.md) 草案的 `/api/v1/collaboration/*` 路径不再使用。补充端点（docs/17）：`GET /api/v1/calendar/rooms/all`（会议室下拉）、`GET /api/v1/calendar/bookings/{id}`（预约详情）、`GET/POST /api/v1/tasks/{id}/members` 与 `DELETE /api/v1/tasks/{id}/members/{userId}`（任务协作者）、`GET /api/v1/tasks/{id}/activities`（活动与评论）。
- 权限串 `cl:event:add|edit|remove`（日程自助；对象级仅组织者可修改/删除/取消，参与人接受/拒绝仅限本人邀请）、`cl:room:add|edit|remove`（会议室维护，HR 授权）、`cl:room:book`（预约/签到/取消）、`cl:task:add|edit|remove`（任务自助；对象级仅指派人/负责人可管理）。日程可见性按 1.7「日程限组织者/参与人」并由可见范围（私有/参与人/本部门/全员）扩展，超管可见全部；无管理范围旁路。
- 时间点为 RFC 3339 带偏移，数据库存 UTC；预约必须携带 Idempotency-Key（1.7），同键重放返回首次结果、同键异请求 409；日程与任务修改提交 `lockVersion` 条件更新，失败返回 409 `VERSION_CONFLICT`；取消/签到/状态跳转由状态条件更新保证只生效一次（重复返回 409 `CL_STATE_CONFLICT`）。

---

## 10. 报表（RP）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/report/dashboard/workbench` | 工作台数据 |
| GET | `/api/v1/report/flow/statistics` | 审批看板 |
| GET | `/api/v1/report/hr/statistics` | 人事看板 |
| GET | `/api/v1/report/attendance/statistics` | 考勤看板 |
| GET | `/api/v1/report/finance/statistics` | 财务看板 |
| GET | `/api/v1/report/export/{type}` | 导出报表 |

**工作台响应**：
```json
{
  "code": 200,
  "data": {
    "todoCount": 5,
    "initiatedCount": 3,
    "todayEvents": [
      {"id": "1", "title": "周会", "startTime": "2026-10-02T14:00:00+08:00"}
    ],
    "notices": [
      {"id": "1", "title": "国庆放假通知", "publishTime": "2026-09-28T09:00:00+08:00"}
    ],
    "attendance": {
      "todayPunched": true,
      "punchInTime": "2026-10-02T09:05:23+08:00",
      "punchOutTime": null
    }
  }
}
```

**审批看板响应**：
```json
{
  "code": 200,
  "data": {
    "todoCount": 8,
    "weekHandled": 25,
    "timeoutCount": 2,
    "avgDurationHours": 24.5,
    "trend": [
      {"date": "2026-09-26", "count": 5},
      {"date": "2026-09-27", "count": 3}
    ],
    "byType": [
      {"type": "请假", "count": 12},
      {"type": "报销", "count": 8}
    ],
    "topSlow": [
      {"processName": "采购申请", "avgHours": 72}
    ]
  }
}
```

### 10.1 权限与实现口径（M8 交付补充）

第 10 节接口的权限串与补充端点（docs/18 落地口径，[18](18-module-plan-reporting.md) 实施记录同步）：

| 权限串 | 覆盖接口 |
|---|---|
| 登录即可 | `GET /report/dashboard/workbench`（管理视角卡片按 `rp:report:list` 过滤）、`GET /report/exports`（本人记录）、`GET /report/exports/{id}`（本人记录） |
| `rp:report:list` | `GET /report/hr/statistics`、`/report/attendance/statistics`、`/report/finance/statistics`、`/report/flow/statistics`、`/report/metrics`、`/report/details/{type}` |
| `rp:report:export` | `GET /report/export/{type}`（同步导出）、`POST /report/exports`（异步导出任务）、导出记录全量查看 |

补充端点（docs/18 步骤 2–4）：`GET /api/v1/report/metrics`（指标字典与当前口径版本）、`GET /api/v1/report/details/{type}`（钻取明细，type 取 `hr|attendance|finance|flow`，与看板/导出同一口径与数据范围）、`POST /api/v1/report/exports` + `GET /api/v1/report/exports`、`GET /api/v1/report/exports/{id}`（导出任务记录：操作者、过滤条件、口径版本快照与私有文件）。`GET /api/v1/report/export/{type}` 支持 `format=CSV|XLSX`、`startDate`/`endDate`（yyyy-MM-dd 闭区间自然日）、`deptId`，行数上限 10000；异步导出必须携带 `Idempotency-Key`（同键同请求重放首次结果、同键异请求 409），行数上限 50000，完成后经 `/api/v1/files/{fileId}/download` 按属主下载。本文档草案的 `/api/v1/reports/overview|attendance|finance|workflow|exports/{id}` 路径不再使用（与 11–17 号实施记录同规则对齐 05）。

权限与数据范围：`rp:report:list`/`rp:report:export` 需显式授权（超管旁路）；数据范围为超管或 `hr`/`finance`/`director` 角色看全量，其余角色只看本部门，导出与列表使用同一权限和筛选口径（docs/02 13.3 RP-07）。报表只读，不修改业务事实；指标口径以 `oa_report_metric_version` 登记的版本为准，口径变更即升版本，导出记录保留当时的版本快照。敏感字段（手机号/身份证/邮箱）在报表明细与导出中一律脱敏。

---

## 11. 系统管理（继承 RuoYi-Vue-Plus）

仅列出扩展接口，其余复用底座。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/system/users` | 用户列表 |
| POST | `/api/v1/system/users` | 新增用户 |
| PUT | `/api/v1/system/users/{id}` | 更新 |
| GET | `/api/v1/system/roles` | 角色列表 |
| GET | `/api/v1/system/menus` | 菜单树 |
| GET | `/api/v1/system/dict/{dictType}` | 字典值 |
| GET | `/api/v1/system/logs` | 操作日志 |
| GET | `/api/v1/system/login-logs` | 登录日志 |

---

## 12. WebSocket 接口

### 12.1 连接

先通过带 Bearer 的 `POST /api/v1/auth/ws-ticket` 获取一次性 ticket，有效期 60 秒，绑定用户/会话，仅供 WebSocket 握手使用。再通过 `wss://oa.example.com/ws?ticket={ticket}` 连接；服务端原子消费 ticket 并校验 Origin 白名单。访问日志不得记录 ticket 或查询参数。会话过期/注销后关闭连接，重新认证取新 ticket。

协议为原生 WebSocket JSON，不使用 STOMP/SockJS；每 30 秒心跳，服务端空闲 90 秒断开。断线指数退避重连（上限 30 秒并加随机抖动），重连后补拉自己的站内信。

### 12.2 消息协议

**下行消息（服务端 → 客户端）**：
```json
{
  "messageId": "9001",
  "type": "TODO",
  "title": "您有一条新的待办",
  "content": "张三的请假申请",
  "bizType": "flow_instance",
  "bizId": "100",
  "url": "/flow/todo/100",
  "timestamp": "2026-10-02T10:00:00+08:00"
}
```

**type 取值**：`TODO`（待办）/ `NOTICE`（公告）/ `MENTION`（提及）/ `SYSTEM`（系统）/ `HEARTBEAT`（心跳）

**上行消息（客户端 → 服务端）**：
```json
{"type": "HEARTBEAT", "timestamp": "2026-10-02T10:00:00+08:00"}
```

---

消息先持久化，再推送；客户端以 messageId 去重，消息已读通过 REST 接口确认。url 是允许列表内的站内相对路径，不能包含任意外部重定向。服务端只向所属用户连接推送，不接受客户端指定其他 userId 的订阅。

## 13. API 版本策略

- **路径版本**：`/api/v1/`、`/api/v2/`
- **兼容原则**：
  - 新增字段向后兼容（客户端可忽略未知字段）
  - 修改语义需升版本
  - 删除字段需提前 3 个月标记 deprecated
- **设计版本**：v1；M0 通用认证、文件、通知和 WebSocket 接口已实现，业务接口按 11–21 号模块计划逐步实现

---

## 14. 调试工具

- **Swagger UI / OpenAPI JSON**：M0 根据真实构建配置记录地址，生产仅受控访问
- **Postman Collection**：`docs/postman/agentoa.postman_collection.json`（二期）

### 14.1 通用附件接口（P0）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/files` | multipart 单文件上传，最大 20 MiB；返回暂存 fileId，不返回内部对象 URL |
| GET | `/api/v1/files/{id}/download` | 业务对象授权后返回字节流 |

上传者只能绑定自己未占用的暂存文件；绑定后按业务对象权限下载。文件扩展名、内容类型和实际内容共同校验，附件不得通过修改 fileId 引用他人的私密文件。知识库文件接口复用此存储服务并增加空间授权。

---

## 15. AI 接口（/api/v1/ai，已实施）

AI 接口契约以 [21 · AI 能力需求与分期计划](21-module-plan-ai.md) §3.3/§4.3/§5.3/§6.3/§7.3 为准，本节为汇总索引；错误码沿用全局 envelope（超限 `AI_QUOTA_EXCEEDED` 429、能力不匹配 `MODEL_CAPABILITY_MISMATCH` 400、上游故障 `LLM_UPSTREAM_ERROR` 502）。

| 阶段 | 方法与路径 | 说明 |
|---|---|---|
| M1 | `GET/POST /providers`、`GET/PUT/DELETE /providers/{id}`、`PUT /providers/{id}/status`、`POST /providers/{id}/test` | 渠道管理与测试连接；Key 仅脱敏回显 |
| M1 | `GET/POST /models`、`GET/PUT/DELETE /models/{id}`、`GET /models/enabled` | 模型管理；enabled 列表登录即可 |
| M1 | `GET /usage/stats`、`GET /usage/logs`、`GET /usage/export`、`GET/POST /quotas`、`PUT/DELETE /quotas/{id}` | 用量统计与配额 |
| M2 | `GET/POST /chat/conversations`、`GET/PUT/DELETE /chat/conversations/{id}`、`GET /chat/conversations/{id}/messages` | 会话与消息（本人可见） |
| M2 | `POST /chat/completions`（SSE）、`POST /chat/messages/{id}/stop`、`POST /chat/messages/{id}/regenerate` | 流式补全、停止、重新生成 |
| M3 | `GET/POST /kb`、`/kb/{id}/members`、`/kb/{id}/sources`、`POST /kb/{id}/search-test`、`POST /qa/ask`（SSE） | 知识域/数据源/检索测试/问答 |
| M4 | `GET /copilot/scenes`、`PUT /copilot/scenes/{code}`、`POST /copilot/{scene}`（SSE）、`GET /copilot/tasks` | 场景开关与生成 |
| M5 | `GET/POST /tools`、`POST /tools/{id}/test`、`GET/POST /agents`、`POST /agents/{id}/run`（SSE）、`GET /agents/runs` | 工具/MCP/Agent |

SSE 响应为 `text/event-stream`（事件 `delta/done/error/usage`），不套用全局 envelope；流式首 token P95 ≤ 3s 目标见 [21](21-module-plan-ai.md) §8。

---

**相关文档**：
- [04-database-design.md](04-database-design.md) · 数据库设计
- [06-deployment.md](06-deployment.md) · 部署运维
