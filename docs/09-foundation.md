# 09 · M0 基础底座使用与验收

> 更新：2026-10-02。本文记录实际工程；01–07 中的八大业务模块仍按路线图实施。

## 1. 已实现范围

| 能力 | 实际实现 |
|---|---|
| 工程 | 官方 RuoYi-Vue-Plus 与配套 plus-ui 源码，保留模块边界与 MIT 声明 |
| 身份/权限 | 用户、部门、岗位、角色、菜单、数据权限、登录/操作日志；单组织；权限每次请求刷新 |
| 会话 | Sa-Token Redis 不透明 Bearer；绝对 2 小时、闲置 30 分钟；退出/重置密码/停用失效 |
| 管理员 | 空库只留禁用占位账号；随机初始化凭据；首次必须改密；重复启动不重置 |
| 迁移 | Flyway V1–V3；独立迁移容器创建 Flowable schema；运行账号仅 DML，运行时 DDL 关闭 |
| 工作流 | Flowable 7.2.0，共享 Spring 事务；固定验证流程及本人任务完成；没有启用 Warm-Flow |
| 文件 | S3 私有桶、专用账号；20 MiB 上限、类型/签名检查、所有者鉴权、SHA256 元数据 |
| 消息 | 事务 outbox → 持久站内信 → 原生 WS；失败重试 5 次；HTTP 补拉与已读隔离 |
| WebSocket | 60 秒单次票据、Origin 白名单、心跳、会话检查；票据不写访问日志 |
| 前端 | 登录/首次改密、组织权限管理、私有文件、消息列表、流程验证；token 只在内存 |
| 工程运维 | 固定版本、npm lock、构建脚本、Compose、健康检查、日志轮转、CI、停写备份脚本 |

新建账号与管理员重置密码均需符合 12 字符/72 UTF-8 字节上限；新账号及重置后的用户首次登录改密。旧版导入与头像上传暂未开放，避免绕开独立密码和私有文件规则。

## 2. 版本与决策

后端：RuoYi-Vue-Plus 5.6.2、Spring Boot 3.5.15、Java 17 字节码、Sa-Token 1.45.0、MyBatis-Plus 3.5.16、Flowable 7.2.0、Flyway 11.7.2。前端：Vue 3.5.30、TypeScript 5.9.3、Vite 7.3.2、Element Plus 2.13.5；锁文件为 npm `package-lock.json`。

本机用 Java 21.0.11、Maven 3.6.3、Node 24.14.0 / npm 11.9.0 构建；后端容器使用 Java 17.0.16。MySQL 8.4.6、Redis 7.4.5、SeaweedFS 4.48、Nginx 1.28.0。精确上游 commit、归档校验和及许可证见 [THIRD_PARTY](../THIRD_PARTY.md)，镜像引用见 [Compose](../deploy/compose.yml)。这些是验证过的固定版本，不表示未来无需安全更新。

选择 5.6.2 是为遵守 Boot 3 基线；上游 v6 已进入 Boot 4。上游配套工作流为 Warm-Flow，不能冒充 Flowable；本工程将其从运行依赖移除，将前端示例移至 `upstream-reference/frontend/`。

MinIO 官方下载站于本次核验返回 HTTP 410，并说明社区项目已归档。经用户选择，默认自建存储改用 SeaweedFS 官方维护者镜像并固定摘要。应用只依赖 S3，可通过 endpoint/凭据切换到其他兼容服务。

## 3. 启动与凭据

前提：JDK 17+、Maven、Node 24.14.0、Docker Engine/Compose v2，首次构建可访问 Maven Central、npm、ECR、GHCR；本机 Windows 使用 Docker Desktop Linux containers。

```powershell
# 在仓库根目录执行；JAVA_HOME 指向本机 JDK。
./scripts/start-local.ps1 -Build
```

如 PowerShell 本机策略禁止脚本，可只对此进程使用 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Build`。已有构建产物时省略 `-Build`。

- 页面：`http://localhost:18080`；开发直连后端：`http://localhost:18081`。
- 用户名 `admin`；初始密码在本机 `deploy/secrets/bootstrap-password`，不提供公共默认值。
- 本次自动验收已执行管理员首次改密，随后按用户要求将本地管理员改为 **`amdin`** 并设置指定密码，取消该账号强制改密。当前账号/密码分别在 `deploy/secrets/admin-current-username`、`deploy/secrets/admin-current-password`。新部署仍使用 `admin` 和 bootstrap 文件。
- 不将密码复制到文档/提交记录。secret 目录需由部署用户独占，并在主机备份中单独加密保管；Compose 文件挂载不是外部密钥管理服务。
- 刷新页面会重新登录，这是内存会话的明确行为。
- 默认启用验证码。`compose.smoke.yml` 仅用于本地自动验收，不用于正常部署。

单独构建：`./scripts/build-backend.ps1`；`./scripts/build-frontend.ps1 -Install`。前端先 Vite 构建生成上游自动导入声明，再执行全量 vue-tsc。后端教学样例 `org/dromara/test/**` 故意含失败断言/外部服务依赖，因此排除；AgentOA 测试照常运行。

`docker compose -f deploy/compose.yml down` 停止服务而保留卷。不要随意加 `-v`；初始化 secret 只作用于新卷，修改 secret 文件不会自动轮换已有数据库账号密码。

## 4. 目录与扩展点

```text
agentoa-backend/
  ruoyi-admin/                  HTTP 入口及登录适配
  ruoyi-modules/agentoa-foundation/
    src/main/java/org/dromara/agentoa/
      config/                   初始化、迁移、事务和账号保护
      file/                     私有 S3 文件
      message/                  outbox、站内信、WebSocket
      web/                      统一响应、异常、requestId、验证接口
    src/main/resources/db/migration/
    src/main/resources/foundation/
    src/test/                   Flowable 事务验证
agentoa-frontend/               官方管理界面与 AgentOA 工作台
deploy/                        本地部署与初始化配置
scripts/                       构建、启动、验收、备份
.github/workflows/verify.yml    Linux 构建及隔离部署验收
upstream-reference/            不加载的上游示例/环境配置
```

M1 新模块依赖 foundation，业务提交用 Spring `@Transactional`；业务记录、引擎调用、outbox 写入必须使用同一个数据源与事务管理器。禁止以异步启动流程代替同事务提交。通过新增 V4+ 演进数据库，不修改已执行迁移的校验和。

## 5. 当前接口

所有新接口位于 `/api/v1`，返回 `code/msg/data/timestamp/requestId`，错误使用对应 HTTP 状态。文件下载返回字节流。继承的 `/auth/*`、`/system/*`、`/monitor/*` 保留上游协议，部分错误仍是 HTTP 200 + 业务 code；不能把该兼容层误当成新 API 规范。

| 接口 | 用途 |
|---|---|
| POST `/api/v1/auth/login` | 用户名密码和可选 captchaCode/captchaUuid |
| GET `/api/v1/auth/profile` | 会话身份、角色/权限、mustChangePassword |
| PUT `/api/v1/auth/password` | oldPassword/newPassword；更新后所有会话注销 |
| POST `/api/v1/auth/logout` | 当前会话注销 |
| POST `/api/v1/auth/ws-ticket` | 单次票据；连接 `/ws?ticket=...` |
| GET/POST `/api/v1/files` | 本人最近 100 个文件 / multipart `file` 上传 |
| GET `/api/v1/files/{id}/download` | 本人文件下载；不存在或非本人均 404 |
| GET `/api/v1/messages` | pageNum/pageSize 分页补拉 |
| GET `/api/v1/messages/unread-count` | 未读数 |
| PUT `/api/v1/messages/{id}/read` | 本人消息标记已读 |
| GET `/api/v1/foundation/overview` | 管理员底座信息 |
| POST `/api/v1/foundation/probes` | 管理员新建测试流程及事务通知 |
| POST `/api/v1/foundation/probes/{processId}/complete` | 完成本人测试任务；再次完成 404 |

验证流程只是管理员工程工具，不是通用业务申请 API；不具备业务申请幂等键、审批历史版本和六模板能力。正式业务在 M1/M2 按 05 规范交付。

## 6. 验证与证据

- 后端 reactor 构建及 Flowable/H2 共同提交、回滚、任务完成测试。
- 前端生产构建、全量 TypeScript 检查。
- MySQL 空库 V1/V2 和后续 V3 升级；同版本重复迁移成功；运行账号不具备 DDL 权限。
- `scripts/smoke.mjs`：最终 55 项 HTTP/WebSocket 检查通过，包括未登录拒绝、首次改密、退出失效、管理员重置后会话失效及再次强制改密、文件上传/下载/伪类型拒绝、普通用户 RBAC 与文件/消息隔离、停用会话、Flowable 完成/重复完成、outbox 落库、WS 心跳及票据重放拒绝。
- `scripts/browser-smoke.mjs`：实际 Edge 无头浏览器登录、工作台、WS、流程操作、无密码/token 持久化、刷新重新登录；截图 `.cache/workbench.png`。该脚本需要 `.cache/browser` 中的 Playwright 1.58.2 与本机 Edge，不是默认构建依赖。
- 最新本机结果：`.cache/backend-build.log`、`.cache/frontend-build.log`、`.cache/smoke-result.json`。缓存不提交；CI 将在代码推送到支持 GitHub Actions 的仓库后执行，本次尚未有远程 CI 运行记录。

```powershell
docker compose -f deploy/compose.yml -f deploy/compose.smoke.yml up -d --wait --wait-timeout 240
node scripts/smoke.mjs
# 恢复验证码
docker compose -f deploy/compose.yml up -d --wait --wait-timeout 240
```

验收脚本创建明确命名的测试用户/角色并清理，保留测试文件、通知与流程历史用于排查；只对本地测试环境运行。首次验收会改变管理员初始密码并将当前值保存在忽略的 secret 文件。

## 7. 运维边界

`scripts/backup-local.ps1` 在短暂停止 backend/storage 写入后导出 MySQL 并归档 SeaweedFS 数据卷，生成 SHA256，最后重启服务。数据库和文件快照配套保留；Redis 不是业务账本，恢复后要求重新登录。备份默认位于 `deploy/backups/`，需另行加密异机复制；不自动删除旧备份。

本次实际生成 `deploy/backups/20261002-125621/`，数据库导出 181736 字节、存储归档 8994 字节；SHA256 与 tar 完整性校验通过。SQL 导入独立测试 schema 后，恢复 3 条文件元数据、5 条消息及 3 条迁移记录。此验证覆盖逻辑数据库恢复与归档完整性，**没有替代文件卷恢复后的完整服务演练或异机灾难恢复**。实际 `SHOW GRANTS` 确认运行账号只有 SELECT/INSERT/UPDATE/DELETE。

恢复应在隔离环境先验证哈希、导入数据库和恢复存储卷，使用原 S3 凭据，保持新卷中的最小权限账号/secret 一致，再验证文件和流程。不得直接覆盖运行环境；尚未测量 RPO/RTO，也没有宣称达到 99.5% 可用性。

当前 Compose 是**仅绑定本机地址的开发/单机验收环境**。实际生产发布前仍需配置真实域名 HTTPS/WSS、外部密钥管理、异机备份恢复演练、资源容量及监控告警。文件目前仅有本人权限与格式检查，业务附件绑定/下载审计策略、恶意文件扫描和大文件分段上传留到对应业务阶段。Outbox 达到 5 次失败转 FAILED，需运维排查后显式重投，尚未提供管理页面。

未实现：调岗调薪、合同与教育经历（P1）、会议室预约、H5 定位/拍照验证、知识库 P1/P2 项（文件夹覆盖权限、评论、Office）；模块 1–6 P0（组织人事、流程审批、考勤、报销财务、公告通知、知识库）已按 10 的顺序交付（见第 8–13 节），其余模块继续推进。

## 8. M1 组织人事模块（V4，2026-10-02）

模块 1（组织人事）P0 已按 [11](11-module-plan-hr.md) 与 [05](05-api-spec.md) 第 3 节契约交付：

- 迁移 `V4__hr_organization.sql`（位于 `ruoyi-modules/agentoa-hr`）：`oa_employee`、`oa_employee_history`、`oa_onboarding`、`oa_offboarding`、`oa_job_position`、`oa_idempotency`；`sys_dept/sys_post` 业务唯一索引；`hr_employee_status` 字典；`hr`/`dept_manager`/`employee` 三个角色与 HR 菜单（`hr:dept:*`、`hr:post:*`、`hr:employee:*`）。空库迁移与既有数据升级（V1→V4 顺序执行）均验证通过。
- 后端 `ruoyi-modules/agentoa-hr`（`org.dromara.agentoa.hr`，MyBatis-Plus）：部门树（循环引用/停用含员工/删除引用保护）、业务岗位（编码唯一、员工引用保护）、员工档案（工号自动生成且唯一键冲突重试、身份证/手机按 `hr:employee:sensitive` 脱敏）、生命周期命令（入职/转正/离职/待离职/停用，状态机校验 + 历史事件唯一；离职与账号冻结同事务并注销会话）、Excel 导入（逐行校验报告，存在错误行整批不落库）、导出（按敏感授权分两档）、`Idempotency-Key` 幂等（同键同请求重放首次结果、同键不同请求 409、保留 24 小时）。
- 前端 `views/hr/{dept,post,employee}`：组织树、岗位管理、花名册（筛选分页、详情含变动历史、入职/转正/离职操作、导入报告、导出）。
- 接口（docs/05）：`GET/POST/PUT/DELETE /api/v1/hr/departments`（`/tree`、`/{id}`、`/{id}/status`）、`/api/v1/hr/posts`（`/{id}`）、`/api/v1/hr/employees`（`/{id}`、`/{id}/changes`、`/{id}/profile`、`/{id}/status`）、`/onboard`、`/regularize`、`/offboard`、`/import`（`/template`）、`/export`；列表返回 `data.records/total/pageNum/pageSize/pages`，`orderBy` 白名单。
- 验证证据：`agentoa-hr` 34 个测试（状态机、角色矩阵、导入校验、H2 生命周期幂等/工号并发重试/越权/事务回滚/账号冻结/幂等记录）；`smoke.mjs` 55 项全部通过（含 14 项 HR 契约检查：幂等重放与 409、重复转正/离职单事件、岗位与档案删除保护、普通员工 403）；前端 `build:prod` + `vue-tsc` 通过；`browser-smoke.mjs` 通过。
- P0 边界（与 docs/05 全量契约的差异）：入职命令暂不自动创建登录账号（账号经系统用户管理建立后以 `userId` 关联，`GET` 响应中 `userId` 可为空）；合同、教育经历、调岗调薪与 lifecycle-requests 承接单为 P1；离职审批（LEAVE_PENDING 流程）留待模块 2 接入，`workflow_instance_id` 已预留。


## 9. M2 流程审批模块（V5，2026-10-03）

模块 2（流程审批）P0 已按 [12](12-module-plan-workflow.md) 与 [05](05-api-spec.md) 第 4 节契约交付：

- 迁移 `V5__workflow_platform.sql`（位于 `ruoyi-modules/agentoa-workflow`）：流程平台八表 `oa_flow_category`、`oa_flow_definition`、`oa_flow_definition_version`、`oa_flow_form_version`、`oa_flow_instance`、`oa_flow_task_action`、`oa_flow_business_ref`、`oa_flow_idempotency`；六模板业务承接单 `oa_leave_request`、`oa_overtime_request`、`oa_correction_request`、`oa_reimburse_request`、`oa_lifecycle_request`；`wf_instance_status`/`wf_task_action`/`at_leave_type`/`at_overtime_type`/`fn_reimburse_status`/`wf_request_status` 字典；`finance`/`cashier`/`director` 角色与 `wf:*` 菜单。空库迁移（V1→V5）与既有数据升级（V4→V5）均验证通过。模板/表单行不落种子 SQL，由代码仓库 `TemplateRegistrar` 启动注册（SHA-256 校验摘要 + 受控校验摘要）并发布。
- 后端 `ruoyi-modules/agentoa-workflow`（`org.dromara.agentoa.workflow`，Flowable 7.2.0 + MyBatis-Plus）：受控模板注册与发布（六固定模板 BPMN + 表单版本，`DRAFT/PUBLISHED/RETIRED`，发布后不可变；代码模板变更自动新增版本，历史实例不受影响）；办理人解析器（SELF/LEADER/DEPT_HEAD/ROLE:key/WHITELIST，禁止 SpEL、Bean 调用与用户脚本，解析失败确定性报错）；流程编排事务（承接单校验、指定版本实例、`oa_flow_business_ref` 关联与 `sys_outbox` 通知同事务提交，失败全部回滚）；任务动作（同意/拒绝/转办/撤销，`oa_flow_task_action` 记录操作者、旧/新办理人、意见与时间）；`Idempotency-Key` 幂等（`oa_flow_idempotency`，同键同请求重放首次结果、同键异请求 409、保留 24 小时）；对象级授权（发起人/实际参与人/`wf:instance:list` 监控范围，伪造办理人 403、他人实例 404、状态冲突 409）。
- 六模板业务承接（docs/12 顺序 6）：请假/加班/补卡/报销最小承接单（草稿-提交-状态流转，时长/金额服务端计算；报销分支 ≤1000 主管→财务、≤5000 增加出纳、>5000 再加总监）；转正/离职经 `POST /api/v1/hr/lifecycle-requests`（`/{id}/submit`）承接单审批，通过后调用 HR 状态机（转正 PROBATION→ACTIVE；离职按生效日期执行 LEFT 与同事务冻结账号/注销会话，未来生效置 LEAVE_PENDING）。`/api/v1/hr/regularize`、`/api/v1/hr/offboard` 直办命令保留为管理员通道。
- 前端 `views/workflow/{todo,done,mine,detail,definition}` + `api/workflow`：待办处理（同意/拒绝/转办）、已办、我发起（模板表单渲染发起申请、撤销）、流程详情（表单快照、审批历史、流程图高亮）、模板与版本管理（发布、BPMN、流程图、分类）。
- 接口（docs/05）：`GET/POST/PUT/DELETE /api/v1/wf/categories`、`GET/PUT /api/v1/wf/definitions`（`/{id}/publish`、`/{id}/xml`、`/{id}/diagram`）、`/api/v1/wf/forms`（`/{formKey}`）、`POST /api/v1/wf/instances/start`、`GET /api/v1/wf/instances`（`/{id}`、`/{id}/history`、`/{id}/diagram`、`PUT /{id}/revoke`）、`GET /api/v1/wf/tasks/todo|done`、`POST /api/v1/wf/tasks/{id}/complete|transfer`、`POST/GET /api/v1/hr/lifecycle-requests`（`/{id}/submit`）与四类承接单 `/api/v1/attendance/{leaves,overtimes,corrections}`、`/api/v1/finance/reimburses`。
- 验证证据：`agentoa-workflow` 20 个测试（BPMN 受控校验攻击用例、授权矩阵、H2 集成：六模板正常/拒绝/撤销/转办/报销三分支/幂等重放与 409/越权 403 与 404/办理人解析失败全量回滚/版本固定/生命周期审批驱动 HR 状态机与账号冻结）；`smoke.mjs` 159 项全部通过（六模板契约链、幂等重放、报销分支链、生命周期审批）；`browser-smoke.mjs` 通过；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界（与 docs/05 全量契约的差异）：不开放 BPMN 上传（`/wf/definitions/import` 不实现）；挂起/恢复/终止、退回、加签、催办、抄送、委托代理与超时升级为 P1；在线设计器暂缓；请假额度冻结、发票占用与报销付款由模块 3/4 经 `FlowBusinessHandler` 回调接入；转正/离职未来生效日期的调度执行为 P1；日期时间沿用底座 `yyyy-MM-dd HH:mm:ss` 格式（docs/05 1.6 的 RFC 3339 为对外契约口径，M0 适配差异记录在案）。

## 10. M3 考勤打卡模块（V6，2026-10-03）

模块 3（考勤打卡）P0 已按 [13](13-module-plan-attendance.md) 与 [05](05-api-spec.md) 第 5 节契约交付：

- 迁移 `V6__attendance.sql`（位于 `ruoyi-modules/agentoa-attendance`）：规则与台账 `oa_shift`、`oa_attendance_group`、`oa_attendance_member`、`oa_calendar`、`oa_holiday`；打卡与日报 `oa_punch_record`、`oa_attendance_day`；额度账本 `oa_leave_type`、`oa_leave_balance`、`oa_leave_ledger`；生效记录 `oa_overtime`、`oa_correction`（docs/04 `at_*` 草案按 V4 惯例统一 `oa_` 前缀；加班/补卡承接单沿用模块 2 的 `oa_overtime_request`/`oa_correction_request`，此处只存审批生效结果）。`at_punch_type`/`at_day_type`/`at_work_status`/`at_leave_action` 字典，`oa_leave_type` 八假种与固定班/弹性班/跨夜班三条班次种子，`at:*` 菜单（2200–2221）与 `hr`/`dept_manager`/`employee` 角色授权。空库迁移（V1→V6）与既有数据升级（V5→V6）均验证通过。
- 后端 `ruoyi-modules/agentoa-attendance`（`org.dromara.agentoa.attendance`）：班次/考勤组/成员生效区间（区间不重叠、删除前引用检查、规则生效后历史日报保留快照不重算）；工作日历与节假日（日历优先于班次工作日，`MAKEUP` 调休上班判定）；打卡命令（服务器接收时间为事实、秒级精度，原始记录全部保留，迟到/早退/缺卡是并存的计算结果；`Idempotency-Key` 同键重放首次结果；窗口外返回 `ATTENDANCE_WINDOW_INVALID`）；请假额度分钟账本（GRANT/FREEZE/SETTLE/RELEASE/ADJUST 不可变流水，`event_key` 唯一幂等去重，条件更新防透支，超额返回 `LEAVE_BALANCE_INSUFFICIENT`）；考勤日报重算（首次/末次有效打卡、迟到早退、缺卡、请假/加班分钟、异常原因串）。
- 流程回调接入（docs/12 暂缓项交付）：模块 2 新增 `FlowEventListener`/`FlowEventListenerRegistry` 与 `DurationCalculator`/`DurationCalculators` SPI，考勤模块注册 `AttendanceFlowListener` 与 `AttendanceDurationCalculator`，全部在流程编排事务内执行——请假提交冻结额度（同事务回滚）、通过转已用、拒绝/撤销释放；请假/补卡时间重叠与每月补卡上限（3 次）在提交前校验；加班通过生成 `oa_overtime` 核定记录，补卡通过生成 `oa_correction` 与补卡打卡记录（`uk_punch_correction` 保证只生成一次）并重算日报。请假时长按工作日历、班次与休息段服务端计算（未配置考勤组时回退自然时长），不接受客户端时长。
- 前端 `views/attendance/{punch,balance,group,shift,calendar,report}` + `api/attendance`：今日打卡与本人打卡记录（员工）、我的余额与额度账本（含发放/查他人，HR）、考勤组与成员、班次管理、工作日历与节假日、日报/月报与 Excel 导出（HR/经理）。
- 接口（docs/05 第 5 节，权限串见该节补充）：`POST/GET /api/v1/attendance/punch`（`/today`、`/records`）、`/groups`（`/all`、`/{id}`、`/{id}/members`）、`/shifts`（`/all`、`/{id}`）、`/calendar`（`/holidays`）、`/leaves/balance`（`/{userId}`、`/grant`、`/ledger`）、`/days`（`/today`）、`/statistics/daily|monthly|export`；打卡与额度发放必填 `Idempotency-Key`。
- 验证证据：`agentoa-attendance` 36 个测试（班次时间计算：固定班/午休/跨夜/弹性/宽限/窗口分离、权限矩阵、H2 集成：额度发放与幂等、防透支、冻结结算释放、事件重放不重复扣减、请假生命周期与撤销释放、重叠拒绝、补卡上限、加班/补卡生效与日报重算、打卡迟到记录、窗口拒绝、无规则拒绝、幂等重放、缺卡标记、跨夜归属）；`smoke.mjs` 184 项全部通过（新增考勤契约：额度发放重放、打卡幂等、冻结 510 分钟（540−30 午休）与结算对账、账本 FREEZE/SETTLE、他人账本 403、日报/月报/日历）；`browser-smoke.mjs` 通过（含今日打卡/我的余额/考勤报表页面渲染）；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界（与 docs/05 全量契约的差异）：轮班、外勤 GPS/照片、WiFi、反作弊、申诉与复杂假期政策为 P1/P2；跨年请假按开始日期归属单一年度额度（按工作日拆分到各年度为 P1）；不计额度假种在同一余额表累计已用但不设额度上限（`availableMinutes` 返回 null），不承诺法定年假规则，额度由 HR 发放；打卡时间取服务器时钟（M0 适配差异沿用 `yyyy-MM-dd HH:mm:ss`）；补卡打卡记录按补卡时段写入 `punch_type` 1/2 并以 `correction_request_id` 标识补卡来源（docs/04 的 `punch_type=4` 展示语义留 P1）。

## 11. M4 报销财务模块（V7，2026-10-04）

模块 4（报销财务）P0 已按 [14](14-module-plan-finance.md) 与 [05](05-api-spec.md) 第 6 节契约交付：

- 迁移 `V7__finance.sql`（位于 `ruoyi-modules/agentoa-finance`）：费用类型 `oa_expense_type`；发票与占用 `oa_invoice`（身份唯一 + 规范化指纹 + 在途/付款占用列）、`oa_invoice_reservation`（OCCUPY/RELEASE/PAID 流水）；财务明细快照 `oa_expense_item`；付款 `oa_payment_record`（`uk_payment_reimburse` 一次全额付款）；财务事件 `oa_finance_event`（`event_key` 幂等）。docs/14 V7 清单中的 `oa_expense_claim` 沿用模块 2 的 `oa_reimburse_request` 承接单（先例同 M3）。`fn_invoice_type`/`fn_payment_method`/`fn_pay_status`/`fn_finance_action` 字典、五个费用类型种子、`fn:*` 菜单（2300–2317）与六角色授权。空库迁移（V1→V7 段）与既有数据升级（V6→V7）均验证通过。
- 后端 `ruoyi-modules/agentoa-finance`（`org.dromara.agentoa.finance`）：金额对象（API 两位小数字符串，服务端转最小货币单位参与校验）；发票录入规范化去重（身份重复 409）与本人/财务角色的明文与图片下载授权（管理员不自动获得）；发票占用按固定 ID 顺序条件更新，同一发票不得分摊到多条明细或多单，明细金额超发票金额 400；付款登记（`Idempotency-Key` + `lockVersion` + 金额以分核对等于已审批总额，重复付款 409，条件更新 3/5→6）；费用统计（部门 × 费用类型，与明细可对账，财务全量/经理本部门）。
- 流程回调接入：模块 4 注册 `FinanceFlowListener implements FlowEventListener`（编排事务内执行）——提交前校验发票归属/金额/占用，提交时固化 `oa_expense_item` 明细快照并占用发票（事件键幂等），通过后承接单 3→5（待付款，V5 预留 5/6 语义由财务模块持有），拒绝/撤销释放占用（付款后永久占用不可释放）；付款完成给申请人写 outbox 付款通知。
- 前端 `views/finance/{reimburse,invoice,expense-type,payment,report}` + `api/finance`：我的报销（明细行编辑、发票选择、提交/删除）、发票录入与占用状态/图片下载、费用类型树维护、待付款清单与付款登记（出纳）、部门/费用类型统计与 Excel 导出。
- 接口（docs/05 第 6 节，权限串见 6.6 补充）：`/api/v1/finance/expense-types`、`/invoices`（`/{id}/download`）、`/reimburses`（`/{id}/pay`、`/pending`）、`/payments`、`/reports/expense|export`；报销草稿 CRUD/提交沿用模块 2 承接单接口；付款登记必填 `Idempotency-Key`。
- 验证证据：`agentoa-finance` 20 个测试（金额对象、权限矩阵、H2 集成：发票占用与并发单占用、拒绝/撤销释放、付款永久占用、越权与超发票金额、重复引用拒绝、审批转待付款、付款一次/幂等重放/异键 409/金额不符 400/状态与版本冲突 409/付款通知 outbox）；`smoke.mjs` 240+ 项全部通过（新增 M4 契约：费用类型重复编码 409、发票身份重复 409、明细越权 403、提交占用与双单冲突 409、拒绝释放、待付款状态、金额不符 400、员工/管理员付款 403、出纳付款/重放/二次付款 409、付款与报表权限）；`browser-smoke.mjs` 通过（含我的报销/发票管理页面渲染）；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界（与 docs/05 全量契约的差异）：预算、OCR、发票真伪校验、银行支付、会计总账与银行流水为 P1/P2；付款仅支持一次人工全额付款，不支持部分付款/拆票/跨单分摊；落库金额沿用 `DECIMAL(12,2)`（校验口径为最小货币单位）；对模块 2 的登记变更：`ReimburseDetailBo` 增加可选 `invoiceId`/`expenseTypeId`、新增草稿 `DELETE /api/v1/finance/reimburses/{id}`。

## 12. M5 公告通知模块（V8，2026-10-04）

模块 5（公告通知）P0 已按 [15](15-module-plan-notice.md) 与 [05](05-api-spec.md) 第 7 节契约交付：

- 迁移 `V8__notice.sql`（位于 `ruoyi-modules/agentoa-notice`）：公告 `oa_announcement`（内容冻结 + 受众范围 + 生命周期）、受众快照 `oa_announcement_audience`、已读 `oa_announcement_read`（唯一键防重复计数）、通知偏好 `oa_notification_preference`；复用 `nc_message`/`sys_outbox` 并补充列（`biz_type/biz_id/del_flag` 与重投审计列）。`nc_notice_type`/`nc_scope_type`/`nc_msg_type`/`nc_notice_status` 字典、`nt:*` 菜单（2400–2416）与角色授权（HR 发布/撤回/重投，全员公告与消息中心）。空库迁移（V1→V8）与既有数据升级（V7→V8）均验证通过。
- 后端 `ruoyi-modules/agentoa-notice`（`org.dromara.agentoa.notice`）：公告生命周期（仅草稿可改/删，已发布拒绝修改，撤回保留受众与已读审计、原接收人不可见）；受众解析（全员/部门/角色/指定人）并在发布时物化快照，组织变更不追溯；发布幂等键（同键重放首次结果、二次发布 409）；站内信统一适配（业务事务内写 `sys_outbox`，提交后落 `nc_message` 并推 WebSocket，断线分页补拉，`uk_message_event_receiver` 去重）；已读幂等与已读统计（分母为快照人数）；消息软删除；通知偏好仅抑制实时提醒不丢消息；失败事件查询与人工重投（仅 FAILED，重投记 `redeliver_*` 列 + 操作日志双审计）。
- 对底座的登记变更：`MessageController` 增补类型/未读过滤、未读分桶（`total/todo/notice/mention/system`）、`read-all`、软删除；`OutboxDispatcher` 落库写 `biz_type/biz_id`（消息深链），投递锁租约与 5 次退避机制不变。
- 前端 `views/notice/{announcement,message}` + `api/notice`：公告列表/详情/发布/修改/撤回/删除/已读统计（公告管理）；消息中心（未读/类型筛选、已读/全部已读/删除、未读徽标、消息事件查询与重投面板）；WebSocket 只做提醒，数据以接口为准。
- 接口（docs/05 第 7 节，权限串见 7.3 补充）：`/api/v1/notice`（`/list`、`/{id}`、`/{id}/publish`、`/{id}/revoke`、`/{id}/read-status`、`/{id}/read`、`/preferences`）、`/api/v1/messages`（`/unread-count`、`/{id}/read`、`/read-all`、`DELETE /{id}`、`/outbox`、`/outbox/{id}/redeliver`）；发布必填 `Idempotency-Key`。
- 验证证据：`agentoa-notice` 13 个测试（权限矩阵、H2 集成：受众快照四类范围、发布幂等与重复发布 409、已发布冻结与撤回保留审计、已读幂等与统计分母、陌生人 404、偏好抑制提醒不丢消息、outbox 失败查询/重投审计/非失败 409）；`smoke.mjs` 240+ 项全部通过（新增 M5 契约：草稿对非受众不可见、发布快照与幂等重放、二次发布/发布后修改 409、消息投递补拉、未读分桶、已读/全部已读、已读统计、撤回后受众不可见、消息事件越权 403 与消息删除）；`browser-smoke.mjs` 通过（含公告管理/消息中心页面渲染）；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界（与 docs/05 全量契约的差异）：邮件/短信/企业微信、定时推送、复杂订阅为 P1/P2；公告富文本编辑器与附件上传 UI 未做（数据模型已留 `attachments`）；撤回后消息条目不做联动标记（公告侧不可见，消息保留审计）；MENTION 提及解析留 P1；发布接口路径以 05 的 `PUT /{id}/revoke` 为准（草案 `POST /{id}/recall` 不使用）。

## 13. M6 知识库模块（V9，2026-10-04）

模块 6（知识库）P0 已按 [16](16-module-plan-knowledge.md) 与 [05](05-api-spec.md) 第 8 节（8.5 实现口径）交付：

- 迁移 `V9__knowledge.sql`（位置 `ruoyi-modules/agentoa-knowledge`）：`oa_knowledge_space`、`oa_knowledge_member`、`oa_document`、`oa_document_version`、`oa_document_revision`、`oa_document_acl`、`oa_document_file`；目录并入 `oa_document.parent_id` 文档树；回收站用 `deleted_at/deleted_by`；`kn_space_type`/`kn_space_role`/`kn_doc_status` 字典、`kn:*` 菜单（2500–2521）、角色授权（HR/主管/员工全按钮，财务/出纳/总监只读菜单）。空库迁移（V1–V9）与已有数据升级（单步 V9）各执行一次验证通过。
- 新增 `ruoyi-modules/agentoa-knowledge`（`org.dromara.agentoa.knowledge`）：空间/成员（角色 `OWNER/EDITOR/COMMENTER/VIEWER`，授权人/时间留审计，最后 OWNER 保护，成员移除即失效）、文档版本提交（`baseVersion` 条件更新，冲突 409；版本历史只追加不可覆盖；回滚生成新版本）、个人草稿（30 秒自动保存）、发布/归档、回收站（30 天恢复窗口）、文档级 ACL（USER/ROLE 主体与空间角色取较高者）、文件柜（复用 `sys_file` + 私有桶，≤20 MiB 且扩展名/魔数/MIME 三重校验，下载鉴权 + 计数/人/时间审计，预览限图片/PDF/文本）、搜索（`content_text` LIKE + `<em>` 高亮，服务端按授权过滤）。默认无权限，管理员也需显式授权。
- 前端 `views/knowledge/{space,document,files,search,trash}` + `api/knowledge`：空间与成员管理、Markdown 左右分栏编辑（`utils/markdown` 轻量渲染 + 行级 diff）、版本历史/对比/回滚、文件柜上传下载预览、搜索高亮、回收站双页签。
- 接口（docs/05 第 8 节 8.5 口径）：`/api/v1/knowledge/spaces`、`/spaces/{id}/members`、`/documents`、`/documents/{id}/versions|draft|restore|publish|archive|rollback`、`/documents/search`、`/files`、`/files/{id}/download|preview|restore`；权限串 `kn:space:*`、`kn:member:*`、`kn:doc:*`、`kn:file:*`。
- 验证证据：`agentoa-knowledge` 27 个 H2 测试（权限矩阵、越权 404/403、版本并发 409、历史不可覆盖、回收站窗口、删除后搜索不可见、下载审计、文件校验）；`smoke.mjs` 270 项全部通过（模块 6 契约含两角色在同一文档上的可见/可编辑/可下载矩阵）；`browser-smoke.mjs` 通过（知识五个页面渲染）；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界：文件夹覆盖权限（P1）、评论/点赞/收藏（P1）、富文本双编辑/在线 Office（P1/P2）、协作编辑（P2）、ES/高级搜索（按容量评估）、分片上传（P1）未做；目录拖拽排序未做（基础多级目录以 `parent_id` 提供）。

## 14. M7 日程会议任务模块（V10，2026-10-04）

模块 7（日程、会议与任务）P0 已按 [17](17-module-plan-collaboration.md) 与 [05](05-api-spec.md) 第 9 节（9.4 实现口径补充）交付：

- 迁移 `V10__collaboration.sql`：位于 `ruoyi-modules/agentoa-collaboration`，建 `oa_calendar_event`、`oa_calendar_attendee`、`oa_meeting_room`、`oa_room_booking`、`oa_task`、`oa_task_member`、`oa_task_activity`；`cl_event_type`/`cl_visibility`/`cl_room_status`/`cl_booking_status`/`cl_task_status`/`cl_task_priority` 字典、`cl:*` 菜单（2600–2619）与角色授权（HR 全按钮含会议室维护，其余业务角色自助按钮）。空库迁移（V1–V10）与存量库单步升级（V9→V10）各执行一次验证通过。
- 后端 `ruoyi-modules/agentoa-collaboration`：`org.dromara.agentoa.collaboration`。日程可见范围（私有/参与人/本部门/全员）服务端过滤（日程限组织者/参与人，超管旁路）；参与人增删留痕（`REMOVED` 标记不删行）并 outbox 通知（确定性 event_id 通知去重）；接受/拒绝仅限参与人。预约 `Idempotency-Key` 幂等 + `FOR UPDATE` 行锁防重叠（左闭右开）+ 状态条件更新，取消/签到一次生效，取消释放时段保留历史，日程与预约一一对应（`uk_booking_event`）。任务五态状态机（非法跳转 409、终态不可改 409）、锁版本条件更新（`VERSION_CONFLICT`）、协作者与活动/评论通知去重、看板按状态分列。
- 前端 `views/calendar/{event,room}` + `views/task/mine` + `api/collaboration`：日视图（日期切换、邀请响应、参与人与会议室联动）、会议室预约（预约/签到/取消/维护）、我的任务（列表/看板、状态与进度、详情抽屉活动流/评论/协作者、通知入口）。
- 接口（docs/05 第 9 节 9.4 口径）：`/api/v1/calendar/events`（`accept`/`reject`）、`/api/v1/calendar/rooms`（`/all`、`/{id}/bookings`）、`/api/v1/calendar/bookings/{id}/checkin|cancel`、`/api/v1/tasks`（`/board`、`/{id}/status|progress|comments|members|activities`）；权限串 `cl:event:*`、`cl:room:*`（含 `cl:room:book`）、`cl:task:*`。
- 验证证据：`agentoa-collaboration` 25 个 H2 测试（可见范围矩阵、通知去重、锁版本冲突、预约幂等/重叠/取消竞态/签到一次、任务状态机与终态保护、越权 404/403）；`smoke.mjs` 318 项全部通过（模块 7 契约含预约冲突可复现且无重复成功记录、私有日程越权、任务非法跳转与已完成任务修改）；`browser-smoke.mjs` 通过（日程/会议室/任务页面渲染）；前端 `build:prod` + `vue-tsc` 通过。
- P0 边界：重复日程（`repeat_rule` 留位）、任务依赖（`parent_id` 留位）、扫码签到形态、周/月视图、外部日历订阅、会议视频、看板拖拽、MENTION 消息解析均未做（P1/P2）。

## 15. P1 批次一（V12–V16，2026-10-04）

P0 稳定后按用户要求提前实施部分 P1 能力，交付记录如下（其余 P1 项继续留在各模块计划的 P1 清单）：

- **HR P1（V12__hr_p1_extensions）**：`oa_employee_education`/`oa_employee_work`/`oa_employee_change`/`oa_contract` 四表 + `hr_change_type`/`hr_contract_type`/`hr_contract_status` 字典 + 菜单 2800–2814。调岗调薪（`POST /api/v1/hr/changes`，Idempotency-Key 幂等，`source_request_id` 一次生效去重，到期异动 `apply-due` 幂等执行）；薪资 AES-256-GCM 密文（`SalaryCipher`，含 nonce/tag 与密钥版本，`hr:change:sensitive` 才见明文）；合同 CRUD 与续签提醒（`GET /api/v1/hr/contracts/expiring`）；教育/工作经历 CRUD（HR 可维护任意员工、员工本人自助）；员工自助 `GET/PUT /api/v1/hr/profile/self`。前端 `views/hr/{change,contract}` 页面与 API。验证：`agentoa-hr` 48 个 H2 测试（含 `HrP1ExtensionH2Test` 11 项、`SalaryCipherTest` 3 项）。
- **Workflow P1（V13__workflow_p1）**：`oa_flow_cc`/`oa_flow_delegate` 表 + `is_timeout` 标志 + `wf_task_action` 字典 P1 动作 + 菜单 2830–2840。挂起/恢复/终止（`PUT /api/v1/wf/instances/{id}/suspend|resume|terminate`，`wf:instance:*` 权限，终止走 `FlowBusinessHandler.onTerminated` 默认归业务撤销态并保留原因）；退回（`/tasks/{id}/return`，ChangeActivityState 回历史节点或发起节点）；加签（`/tasks/{id}/addsign`，before=前加签共同办理/after=后加签顺序续办）；催办（`/tasks/{id}/urge`，仅发起人，按日去重站内信）；抄送（`POST /instances/{id}/cc` + `GET /tasks/cc`，event_id 幂等）；委托代理（`/api/v1/wf/delegates` CRUD + 待办/办理范围扩展）；超时扫描（`POST /instances/timeout-scan`，`is_timeout` 独立标志 + 每日提醒去重）。前端 `workflow/{monitor,cc,delegate}` 页面、待办页退回/加签/催办操作。验证：`agentoa-workflow` 34 个 H2 测试（含 `WorkflowP1H2Test` 14 项，覆盖挂起拦截办理、终止归业务态、退回节点迁移、后加签顺序、催办/抄送幂等、委托生效与失效、超时打标）。
- **考勤 P1（V14__attendance_p1）**：`oa_punch_record` 增 `photo_file_id`/`wifi_name`，新表 `oa_shift_assignment`（一人一天一条，覆盖考勤组班次）+ 菜单 2815–2818。外勤打卡（`punchType=3`：GPS + 现场照片缺一不可，跳过班次窗口、无排班也可打卡，定位仅作风险信号）；排班指派（`/api/v1/attendance/schedules`，单日/区间，重复日期覆盖）。前端打卡页外勤弹窗（浏览器定位 + `/api/v1/files` 传照片）、`views/attendance/schedule` 页面。验证：`agentoa-attendance` 43 个测试（含 `AttendanceP1H2Test` 7 项）。
- **财务 P1（V15__finance_p1）**：`oa_budget`/`oa_budget_ledger` 表（event_key 幂等闸门 + 条件更新防透支，对齐请假额度账本口径），`oa_reimburse_request` 增 `budget_id`，菜单 2850–2854。预算 CRUD 与使用情况（可用=总额−已用−冻结、预警阈值），报销提交冻结/通过转已用/拒绝撤销释放（`FinanceFlowListener` 挂钩，`BUDGET_INSUFFICIENT` 409）。前端 `views/finance/budget` 页面。验证：`agentoa-finance` 26 个测试（含 `FinanceBudgetH2Test` 6 项，含流程联动与账本幂等）。
- **知识库 P1（V16__knowledge_p1）**：`oa_document_comment`/`oa_document_like`/`oa_document_favorite` 表 + 我的收藏菜单 2860。评论（COMMENTER 及以上，回复、作者/管理员删除）、点赞/收藏幂等（唯一键，重复不计数）、社交统计接口；全部复用文档同一鉴权口径。前端 `knowledge/favorite` 页面与社交 API。验证：`agentoa-knowledge` 32 个测试（含 `KnowledgeP1H2Test` 5 项）。
- 全量验证：`mvn -pl ruoyi-admin -am verify -DskipTests=false` 通过（9 模块 245 个后端测试全绿）；前端 `build:prod` 通过。
- 本批次 P1 边界：会签/或签多实例模板、超时升级链、委托代理的署名规则细化、调休批次滚动过期、跨年请假拆分、部分付款/拆票、通知模板与定时推送、重复日程 RRULE、任务依赖、标签/目录 ACL、在线设计器等仍未做，保留在对应模块计划的 P1 清单。

## 16. P1 批次二（V17–V22，2026-10-05 起）

P1 批次二按交付包推进：A 会签/或签多实例模板（WF-08）、B 部分付款/拆票（FN-05/FN-02）、C 通知模板与定时推送（NC-04）、D 重复日程 RRULE（CL-01）、E 调休批次滚动过期（AT-06）、F 目录 ACL（KB-08）、G 模块 9 运维验收项（docs/19 门槛，非代码）。实施顺序 C → E → A → B → D → F → G。**迁移版本按实施顺序分配**：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（保证 Flyway 版本链线性，与交付包字母编号的对应关系以此处为准）。决策点拍板：①会签任一拒绝即终止实例；②拆票 allocation 表一次做全（同单多明细+跨单分摊），已付清部分的 allocation 永久、未付清随拒绝/撤销释放；③RRULE 子集 DAILY/WEEKLY/MONTHLY/YEARLY + INTERVAL + BYDAY/BYMONTHDAY + COUNT/UNTIL，预物化 12 个月/≤365 条；④定时推送自研 @Scheduled + DB 租约（不引入 SnailJob）；⑤批次过期仅调休（+可选年假）。

- **C 通知模板与定时推送（V17__notice_p1，NC-04）**：`oa_notice_template`（`{var}` 受控占位符白名单，禁表达式）/`oa_scheduled_push`/`oa_scheduled_push_run` 三表 + 内置模板种子（入职欢迎/生日祝福/考勤异常提醒）+ 字典 `nc_push_type`/`nc_schedule_type`/`nc_push_status` + 菜单 2420–2429（`nt:template:*`/`nt:schedule:*`，HR 可管、员工不可）。`NoticeTemplateRenderer`（声明校验 + 缺变量/未声明变量 400）、`PushCron`（5 字段受限子集，未知指令拒绝）、`NoticeAudienceResolver`（受众四类解析，公告/模板同源复用）。`ScheduledPushDispatcher`（`@Scheduled(fixedDelay=10s)` + FOR UPDATE SKIP LOCKED DB 租约 + `event_key=SCHED-{pushId}-{slot}` 幂等去重）；定时公告到点调 `AnnouncementService.publish`（同事务受众物化+outbox，与手动发布一致），模板推送按受众渲染写 outbox；失败落 FAILED 执行记录并推进 slot（周期漏发合并不回放），outbox 失败走既有重投通道；手动兜底 `POST /api/v1/notice/push/run`。前端 `views/notice/{template,schedule}` 页面（模板 CRUD/变量声明/发送、推送 CRUD/暂停恢复/执行历史）。验证：`agentoa-notice` 35 个测试（新增模板/调度 H2 11 项 + 渲染器/cron 11 项），全量 verify 通过，前端 build+vue-tsc 通过，`smoke.mjs` 增 Module 5b 契约链（模板发送/定时触发/重扫不重复/权限矩阵）。
- **E 调休批次滚动过期（V18__attendance_leave_batch，AT-06）**：`oa_leave_batch`/`oa_leave_batch_allocation` 两表（docs/04 5.7）+ 历史余额迁移为单批次；`oa_leave_balance` 汇总行与唯一键不动。批次管理假种=调休+年假（决策点 5），发放即建批次（`BalanceGrantBo.validFrom/expireDate`，调休默认 +3 个月、年假默认当年 12-31）；消耗 FIFO 先过期先扣（expire_date ASC、条件更新防透支），结算/释放按冻结分配原批回退（历史在途冻结按 FIFO 兜底）；不变式 SUM(批次剩余)==余额可用（分配表对账）。过期扫描 `POST /api/v1/attendance/leaves/expire-scan`（`EXPIRE:{batchId}` 幂等，清零可用并写 ADJUST 流水）+ `LeaveBatchExpiryDispatcher` 小时级自愈触发（复用 C 调度骨架）；冻结过期保护至结算（结算仍成功）、过期批次释放归零（同步回收总额 + `RELEASE-WO:` ADJUST 流水）；回收（负发放）按 LIFO 缩减批次。前端余额页"额度批次"区块（批次号/额度/冻结/已用/已过期/剩余/有效期/状态 + 过期扫描按钮）、发放弹窗有效期字段。验证：`agentoa-attendance` 53 个测试（新增 `LeaveBatchH2Test` 10 项：FIFO 跨批次、防透支回滚、alloc 对账、过期清零幂等、冻结保护结算、释放归零、SUM 对账、默认有效期与回收 LIFO），全量 verify 与前端构建通过，`smoke.mjs` 增 Module 3b（批次创建/幂等重放/越权 403/过期扫描幂等）。
- 本批次 P1 边界（随交付包更新）：会签/或签多实例模板、部分付款/拆票、重复日程 RRULE、目录 ACL 仍待交付；跨年请假拆分、批次月报口径细化、邮件/短信/企业微信渠道、模板市场/继承、cron 全量语法（L/W/#、名称）、定时推送集群分布式锁服务（现为 DB 租约）、在线设计器仍未做。

## 17. 模块 10 AI 能力（M1–M5，V28–V32，2026-10-06）

模块 10 按 [21](21-module-plan-ai.md) 分期交付：M1 模型接入层 + M2 AI 助手对话 + M3 知识库问答（RAG）+ M4 业务 Copilot + M5 Agent/MCP 全部交付。

- **M1 模型接入层（V28__ai_foundation）**：`oa_ai_provider`/`oa_ai_model`/`oa_ai_usage_log`/`oa_ai_quota` 四表 + 字典 `ai_provider_type`/`ai_model_capability`/`ai_usage_status`/`ai_quota_scope`/`ai_quota_period` + 菜单 3020–3063（AI 管理，仅管理员）。Key 双通道（AES-256-GCM 密文同薪资口径 `AiKeyCipher` + `secret_ref` 环境变量/configtree 优先，回显仅 `sk-***ab12` hint）；`LlmGateway` 统一入口（限额判定、用量异步落库、错误归一化、流式空闲看门狗）+ `AiModelRouter`（同模型多渠道按优先级+健康度降级）+ `ProviderRegistry`/`ProviderHealth`；测试连接错误分类（鉴权/超时/余额/模型不存在/上游）。
- **M2 AI 助手对话（V29__ai_chat）**：`oa_ai_conversation`/`oa_ai_message`/`oa_ai_prompt_template` 三表 + 字典 `ai_msg_role` + 菜单 3000/3001/3010（全员）。SSE 流式（`meta/delta/usage/done/error`，绕过 envelope）+ 停止生成（取消上游、消息置 stopped）+ 重生成留旧版本 + 多模态传图（≤5 张、vision 能力校验、私有 S3 三重校验）+ 上下文按 `context_window` 截断 + 5 个内置提示词模板。
- **M3 知识库问答（V30__ai_rag）**：`oa_ai_kb`/`oa_ai_kb_source`/`oa_ai_kb_chunk` 三表（embedding BLOB + 应用内余弦 + 进程内热缓存，`store_type` 预留向量库切换）+ `oa_ai_conversation.scene`/`oa_ai_message.citations` 追加列 + 字典 `ai_kb_index_status`/`ai_kb_visibility` + 菜单 3002/3011（知识问答，全员）与 3025/3064–3068（知识库管理，仅管理员）。管线：`AiTextChunker`（Markdown 去标记 → 标题/段落边界分块，默认 512 token/64 token 重叠）→ `EmbeddingTransport`（LangChain4j OpenAI 兼容向量化，走 `LlmGateway` 限额/日志）→ `KbIndexer`（独立线程池索引，失败置 failed 可重试）→ `KbRetriever`（向量 top-5 + 关键词 LIKE 融合排序）；增量 `KbSourceSyncDispatcher`（@Scheduled 对账：文档新版本重索引、删除失效）。权限：知识域可见性（全员/创建者/显式成员）+ 文档来源按知识库 ACL 可见集服务端过滤（`KnowledgeDocumentAccess` 复用 `KnowledgeAuthorization.authorizedSpaceIds/aclOnlyDocumentIds`，不可见即 404），引用片段不越权泄露；直传文件 PDF/DOCX/TXT/MD（`AiDocumentParser` 三重校验 + PDFBox/POI 解析，经 `sys_file`）。API `/api/v1/ai/kb`（域/成员/数据源/重建索引/检索测试/分块预览）与 `/api/v1/ai/qa`（SSE 问答 done 带引用列表 `title+heading+snippet+link`、历史）。前端 `views/ai/kb`（知识域/数据源/索引状态/检索测试面板/分块预览）与 `views/ai/qa`（问答+引用跳转原文，`knowledge/document?docId=` 深链直达）。
- **M4 业务 Copilot（V31__ai_copilot）**：`oa_ai_copilot_config`（场景开关/模型/模板）+ `oa_ai_copilot_task`（异步生成任务，`input_ref` 留存只读上下文供重试）+ 字典 `ai_copilot_task` + 菜单 3003/3012/3013（业务助手，全员用、场景配置仅管理员）。5 个内置场景（审批摘要/报表解读/通知起草/表单填写建议/会议纪要）+ 5 个内置提示词模板（5006–5010，均带「AI 生成内容仅供参考」声明）。业务上下文走 `CopilotBizReader` 只读 SPI：`WorkflowCopilotReader`（审批单+历史意见，`WorkflowAccessPolicy.canViewInstance`）、`FlowFormCopilotReader`（表单 Schema）、`NoticeCopilotReader`（通知/模板，需 `nt:notice:add`）、`ReportCopilotReader`（报表导出元信息，需 `rp:report:list`）、`MeetingCopilotReader`（日程/会议，组织者与参与人可见）；越权 403/404，缺失实现降级为无上下文。API `/api/v1/ai/copilot`（scenes 配置、`POST /copilot/{scene}` SSE 生成、tasks 列表/详情/异步重试/删除）。前端 `views/ai/copilot`（场景卡片 + 生成抽屉 + 任务列表）。
- **M5 Agent/MCP（V32__ai_agent）**：`oa_ai_tool`（function/mcp、JSON Schema、write_flag）+ `oa_ai_agent`（系统提示/工具集/max_steps/timeout_sec）+ `oa_ai_agent_run`（多步执行轨迹 `trace_json`）+ 字典 `ai_tool_type` + 菜单 3026/3069–3074（工具与 MCP）与 3027/3075–3079（Agent 管理，均仅管理员）。内置 6 个函数工具（知识库检索复用 M3、待办/日程查询、创建日程、通知草稿、通讯录）+ 预置 Agent「办公助手」。工具调用走原生 function calling：`ChatTransport` 扩展 `ToolSpec/ToolCall`，`LangChain4jChatTransport` 映射 `ToolSpecification/ToolExecutionRequest/ToolExecutionResultMessage`；`McpToolGateway` 基于 LangChain4j `DefaultMcpClient` 发现并调用外部 MCP server（鉴权头只写不读）。`AgentEngine` 多步循环（模型→工具→回填→…，max_steps/超时熔断）逐步产出 `thought/tool_call/tool_result/final` 轨迹；写类工具必须人工确认（`approvedWriteTools`）且持有 `ai:tool:write`，否则轨迹记为 skipped，防误调损失。API `/api/v1/ai/tools`（CRUD/测试/发现）与 `/api/v1/ai/agents`（CRUD/`POST /{id}/run` SSE 含 step 轨迹/运行记录与回放）。前端 `views/ai/tool`、`views/ai/agent`（运行抽屉 + el-timeline 轨迹回放）。
- **业务页集成与安全收口**：`src/components/AiCopilotDrawer` 统一侧边抽屉嵌入 5 个业务页——`workflow/detail`（AI 摘要，M4-01）、`report/finance`（AI 解读，M4-02）、`notice/announcement` 编辑框（AI 起草并填入内容，M4-03）、`finance/reimburse`（AI 建议采纳到事由，M4-04）、`calendar/event`（AI 纪要写入描述，M4-05），全部标注「AI 生成内容仅供参考」。对话页新增 **Agent 模式**（M5-06：工具调用轨迹随气泡渲染）与**会话重命名**（M2-01）。写类工具按**工具逐项**人工确认（M5-05：`approvedWriteTools` 逐码校验 + `ai:tool:write` 权限，未确认即轨迹记为跳过）；M4-06 任务完成走 `CopilotNotifier` SPI → 通知模板 `ai_task_done` → outbox/WS 站内消息，异步重试改用有界线程池（队满 429）。M5-01 补齐请假余额/报销进度/公告搜索 3 个内置工具；M5-02 支持 MCP 发现工具「纳入工具清单」（`POST /tools/discover/{id}/import`）。
- 验证证据：`agentoa-ai` 66 个 H2 测试全绿（M3 新增 18 项；M4 新增 8 项：场景 bootstrap/停用 403/空输入 400/未知场景 400/生成落任务/任务仅本人可见/异步重试/任务删除越权 404；M5 新增 10 项：工具 CRUD 与码冲突 409/内置锁定 409/无实现测试失败/MCP 鉴权头不回显/Agent CRUD 与内置锁定/工具调用轨迹/写类需确认/写类按工具逐项确认/运行仅本人可见/停用 403）；全量后端测试通过；前端 `build:prod` + `vue-tsc` 通过；`smoke.mjs` 增 Module 10 M3/M4/M5 契约链，`browser-smoke.mjs` 增 `/ai/copilot`、`/aiadmin/tool`、`/aiadmin/agent` 渲染校验。
- M3 边界与未决项：向量存储 P0 为 MySQL BLOB + 应用内余弦（进程内热缓存，规模化后按 §9.2 切换 Redis/专用向量库）；文档变更采用定时对账（知识库暂无领域事件）；rerank 未用；检索面板暂未提供查询改写与 rerank 开关（AI-M3-06 部分）；M4 业务上下文只接了 5 个只读阅读器（其余业务字段靠调用方摘要）；M5 未接入真实 MCP server（连通性与工具导入由 `POST /tools/{id}/test`、`POST /tools/discover/{id}/import` 验证）。

## 18. 端侧 1 · PC Web F1 工程质量基线（2026-10-07）

端侧 1 按 [22](22-module-plan-frontend.md) F1 期交付工程质量门禁，业务功能面不变（页面、接口、权限均无增删）。

- **FE-F1-01 类型门禁**：`package.json` 增 `typecheck`（`vue-tsc --noEmit`），`scripts/build-frontend.ps1` 与 `.github/workflows/verify.yml` 改调 `npm run typecheck`，保持「先 build 生成 auto-import 声明再检查」的顺序。
- **FE-F1-02 单测防线**：新增 `vitest.config.ts`（node 环境、`include: src/**/__tests__/**/*.test.ts`、复用 `vite/plugins/auto-import` 保持自动导入一致）与 `npm run test`/`test:watch`；首批 4 个用例文件 77 例：`utils/ruoyi.ts`（parseTime/addDateRange/handleTree/tansParams/selectDictLabel(s)/parseStrEmpty/mergeRecursive/getNormalPath/blobValidate）、`utils/index.ts`（formatDate/formatTime/getQueryObject/byteLength/cleanArray/param/param2Obj/objectMerge/class helpers/debounce/deepClone/uniqueArr/createUniqueString/isExternal）、`utils/request.ts`（信封 200/401/500/601/其它码/blob 六分支 + 传输层错误文案映射 + 500ms 重复提交拦截 + 参数序列化与请求头）、`store/modules/permission.ts`（`filterDynamicRoutes` 权限/角色过滤、`loadView` 映射与悬空组件解析为 undefined、store 状态存取）。
- **FE-F1-03 lint 门禁**：存量 194 项清零（193 项 prettier 自动修复；`src/layout/components/TopBar/index.vue` 补 `lang="ts"` 并按 `Sidebar/index.vue` 既有约定改用 `getSidebarRoutes()` + `computed<RouteRecordRaw[]>`、`parseInt`→`Math.floor`），`verify.yml` 与 `build-frontend.ps1` 纳入 `npm run lint:eslint`；四门禁顺序 lint → build → typecheck → test，本地与 CI 同口径。
- **FE-F1-04 元信息与文档**：`package.json` 改名 `agentoa-frontend`、版本 `1.0.0`（对齐 `agentoa-uniapp`），description/author 纠偏并移除指向 plus-ui 的 repository；重写 `agentoa-frontend/README.md`（真实端口 5173/18080、命令清单、目录约定、token 内存态安全口径、指向 docs/22）。
- **FE-F1-05 菜单清理**：新增 `V33__remove_dangling_dict_data_menu.sql`（位于 `ruoyi-modules/agentoa-foundation`，不改已执行迁移）删除 `sys_menu` 132「字典数据」（component=`system/dict/data` 无对应视图，`loadView` 解析为空）及其 `sys_role_menu` 绑定；核对确认 `workflow/leave/leaveEdit`、`workflow/spel` 不在任何 Flyway 种子中（仅存于 `upstream-reference` 与非 Flyway 的 `script/sql/update_*.sql`），无需清理。
- 验证证据：前端四命令全绿（`lint:eslint` 退出码 0 / 77 例测试全绿 / `build:prod` 21.6s / `typecheck` 退出码 0）；后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（含 `agentoa-ai` 66 例）；`sys_menu` 99 个 component 全量核对后仅 menu 132 悬空并已由 V33 移除；V33 不影响迁移双向门禁（`MigrationReversibilityTest` 仅锁定 workflow V23–V27 与 notice 具名迁移）。
- 未包含（F1 退出条件之外，留 F2–F5）：F2 词条接入（策略已定 B：业务仅中文、框架双语）、F3 通知实时化（通道保留按部署配置）、F4 大文件拆分与 `knowledge/favorite` N+1 治理/包体基线/浏览器矩阵与无障碍、F5 可选增强、uniapp 空 `typecheck` 治理（[23](23-module-plan-h5.md) 范围）。
- 实现期偏差登记：`sprintf`（`utils/ruoyi.ts`）全库无调用且存在 `arguments` 遮蔽缺陷（上游遗留），未纳入用例、未改生产代码；`debounce` 尾沿调用因同名参数遮蔽不透传参数（上游遗留），用例按现状锁定合并次数；测试用例落 `src/**/__tests__/`（沿用 `tsconfig.json` 既有 `exclude` 约定，不纳入 `vue-tsc`）。

**F1 验收回归暴露的缺陷修复（2026-10-07，跨模块）**

跑通 `smoke.mjs` + `browser-smoke.mjs` 全链路时，暴露了一批此前被「AI 冒烟段从未跑绿」掩盖的缺陷。冒烟脚本侧修复 6 处（AI 段建用户补强制改密握手、消息投递轮询窗 30s→90s 以匹配 outbox 20 条/3s 排空速率、会议室列表改按名过滤避免分页截断、工具码加时间戳防残留 409、SSE 事件字面量断言容忍冒号空白、M3 错误码断言补 `status:`）；后端契约修复 6 处：

- **模块 10（AI，详见 [21](21-module-plan-ai.md) §9.2）**：① 5 个 SSE 端点补 `produces = text/event-stream` + 显式响应类型，并让 `AccountGuard` 对 `DispatcherType.ASYNC` 短路（消除 `emitter.complete()` 异步分派重跑鉴权链抛 `SaTokenContextException` 后被全局异常器污染事件流的问题）；② `GET /chat/templates` 按 `oa_ai_copilot_config.prompt_template_id` 过滤场景专用模板；③ `AiKbServiceImpl.list`/`AiModelServiceImpl.page` 无过滤参数时 NPE 补空值护栏（+2 条 H2 回归）；④ `McpToolGateway.open` 建连失败归一化为 `AI_MCP_CONNECT_FAILED` 502；⑤ `KbRetriever.search` 把查询向量化提前到可见性短路之前，使检索测试/问答如实暴露 embedding 上游故障。
- **模块 2（流程审批）**：`WorkflowOrchestrator.applyTaskSnapshot` 的展示字段 `current_assignees` 拼接超出 `VARCHAR(512)` 即 500（同角色 26+ 名成员即可触发，属生产可复现缺陷），改为按 500 字符截断。
- 最终验收：`smoke.mjs` **PASS 482 项 HTTP/WebSocket 检查**、`browser-smoke.mjs` PASS、后端 `verify` BUILD SUCCESS（agentoa-ai 68 例）、前端四门禁全绿、迁移链 V28→V33 在 MySQL 8.4 真实执行全部成功。

## 19. 端侧 F3/F4 与 AI 复核补强（2026-10-08）

本批次为 [21](21-module-plan-ai.md)/[22](22-module-plan-frontend.md) 复核落码，无新增迁移（沿用 V1–V33），验证口径与 §18 相同：

- **端侧 F3 通知实时化**（见 22 §7.3）：新增 `utils/realtime.ts`（站内 ticket WS 统一收口：换票/心跳 30s/指数退避重连 1s→30s 封顶）、重写 `store/modules/notice.ts`（推送按 messageId 去重入列 + 未读角标以接口拉取为准的双轨去重、断线 60s 拉取兜底）、重写 `layout/components/notice`（铃铛 + 未读徽标下拉面板，移除上游 gitee 死链接）接入 `Navbar`、工作台连接指示改读 store 状态并联动刷新；登出断链并清空通知态。
- **端侧 F4 体验与性能**（见 22 §7.4）：`hr/employee`/`system/dict`/`ai/kb` 三个大文件拆子组件（keep-alive name 与路由 component 不变）；`knowledge/favorite` N+1 治理——后端新增 `GET /api/v1/knowledge/documents/batch`（服务端授权过滤、上限 200，见 [16](16-module-plan-knowledge.md) 变更记录），前端改一次批量拉取；echarts 按需引入（`utils/echarts.ts`）并归档 dist 体积基线；浏览器矩阵与无障碍验收待人工执行（19 号门槛）。
- **模块 10（AI）代码复核补强**（见 21「代码复核补强」）：接线 `AiQuotaGuard.checkMidStream`（流式产出按 256 token/16 delta 复检限额，超限 `StreamTicket.abort()` 熔断上游并回 429 `AI_QUOTA_EXCEEDED（流式生成已中断）`，部分产出计入用量日志）；补 4 项契约修复的回归用例（场景模板过滤/MCP 归一化/检索向量化顺序/AccountGuard ASYNC 短路）与月周期限额、Agent 步数/超时熔断用例；`McpToolGateway.open()` 归一化范围扩至 transport 构建阶段。
- **知识模块**：`KnowledgeDocumentService.details` 批量详情（`KnowledgeDocumentH2Test#batchDetailsFilterByAuthorizationAndDropDeleted`）。
- **验收证据**：后端 `mvn verify` BUILD SUCCESS（agentoa-ai 77 例、agentoa-knowledge 33 例、agentoa-foundation 含 `AccountGuardTest`）；前端四门禁（`lint:eslint`/`build:prod` 22.6s/`typecheck`/`test` 77 例）全绿；`smoke.mjs` **PASS 493 项 HTTP/WebSocket 检查**；`browser-smoke.mjs` PASS（含拆分后 hr/dict/kb 页面渲染与 WebSocket 段）。

## 20. 未决项定案、F5-03 加固与验证体系补强（2026-10-08 二轮）

本批次为 [21](21-module-plan-ai.md) §9.2 / [22](22-module-plan-frontend.md) §6.1 未决项定案落码，无新增迁移（沿用 V1–V33）：

- **AI M4 最小补强**（见 21「M4 最小补强与 M3 可观测」）：新增 `CopilotTaskRecovery`（启动回收 pending/running 孤儿任务 → failed 可重试）；`retry()` 改 CAS 领取（仅 done/failed 可进入 pending，冲突 409 `AI_COPILOT_TASK_CONFLICT`）+ 执行侧 pending→running 二次 CAS。完整 `@Scheduled` 异步化设触发条件再立项。
- **AI M3 可观测**（支撑向量库切换阈值）：`AiKbVo.chunkCount` 聚合 + `KbRetriever` 检索遥测日志（扫描分块/候选/命中/耗时）；§9.2-1 阈值与后继选型（Redis Stack 首选）定案。**更正**：`langchain4j-mcp` 实为 BOM 管理的 `1.12.2-beta22`（见 21 §9.2-4）。
- **端侧 F5**（见 22 §7.6）：F5-01 关闭（不引入 element-plus-x）、F5-02 转 v1.1 需求池（07 号 P1 候选）；F5-03 交付——`utils/sanitize.ts`（标签/属性白名单 + safeUrl + style 过滤）接入 `components/Editor` 粘贴拦截，图片粘贴/插入统一走 sys_file 上传（`checkEditorImage`，禁 `data:` 内联）。
- **实时通道假死检测**：`utils/realtime.ts` 心跳补应答跟踪（超时未回显即主动断开触发重连降级），修复断网半开套接字检测盲区。
- **验证体系**（见 22 §7.6）：`browser-smoke.mjs` 新增 C1 断网/恢复（`routeWebSocket` 切断 + `setOffline`，CDP 离线不断已建 WS）、C2 消息端到端时延实测（探针→outbox→WS→角标，实测 2611–3101ms）、C3 axe-core 五页初筛（报告 `artifacts/a11y-report.json`，基线 4/11/12/10/9）；补 `/workflow/{todo,done,detail}` 三页渲染覆盖（详情数据经「通用申请」发起预置）；C4 人工验收清单 [24](24-release-acceptance-checklist.md) 挂接 [19](19-module-plan-release.md) 门槛。
- **验收证据**：后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（agentoa-ai **80** 例）；前端四门禁全绿（`test` **110** 例/7 文件）；`smoke.mjs` **PASS 498 项**；`browser-smoke.mjs` PASS（含 C1/C2/C3 与 workflow 三页）。

## 21. 端侧 2 · 移动端 H1–H6（2026-10-08）

- **范围**：[23](23-module-plan-h5.md) H1–H6 全量（H7 真机验收转 [24](24-release-acceptance-checklist.md)）：质量门禁与移动冒烟、goApply 缺陷修复、tabBar/manifest、定位/外勤打卡与幂等重试、表单 file/image 附件与报销发票闭环、财务查询、通讯录/日程/知识库/任务/自助资料、WS 实时消息与提及页签、移动 AI 对话/知识问答（SSE 流式，小程序 `enableChunked` 分段通道）。
- **后端配套（无 DDL）**：`FormSchemaBo.FIELD_TYPES` 增 `file/image`、新增 `GET /api/v1/wf/instances/{id}/attachments/{fileId}/download`（见 [12](12-module-plan-workflow.md) 变更清单）；`PunchRecordVo` 增定位/照片字段（见 [13](13-module-plan-attendance.md) 变更清单）。
- **工程**：`agentoa-uniapp` 三门禁真实生效（`lint:eslint`/`typecheck`=`vue-tsc --noEmit`/双端构建）入 `build-h5.ps1` 与 `verify.yml`；`scripts/h5-smoke.mjs`（10 项断言）、`scripts/gen-h5-tab-icons.mjs`、`agentoa-uniapp/scripts/prepare-mp-weixin.mjs` 新增。
- **验收证据**：后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（含 `FlowAttachmentAccessTest` 7 例、attendance 10 例）；`smoke.mjs` PASS 495 项（分支条件计数）；`h5-smoke.mjs` PASS 10 项；`browser-smoke.mjs` PASS（PC 回归 + C2 时延 1809ms）；uniapp lint/typecheck/build:h5/build:mp-weixin 全绿。
