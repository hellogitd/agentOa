# 端侧 2：H5/移动端（agentoa-uniapp）开发计划

> 版本：v1.1（H1–H6 已实施，未决项 1 定案）｜日期：2026-10-08｜状态：H1–H6 交付（见 §7），H7 真机验收与发布待人工（[24](24-release-acceptance-checklist.md)）
> 关联：[02](02-requirements.md)、[03](03-architecture.md)、[13](13-module-plan-attendance.md)、[19](19-module-plan-release.md)、[20](20-completion-review.md)、[21](21-module-plan-ai.md)、[22](22-module-plan-frontend.md)

## 1. 背景与定位

`agentoa-uniapp` 是 AgentOA 的移动端（uni-app 3 + Vue 3），**H5 为主交付形态，微信小程序同步构建**（`dist/build/{h5,mp-weixin}` 已有产物），APP/支付宝小程序仅 manifest 预留、不排期。现状交付 15 个页面（工作台/审批/考勤/公告/消息/我的），覆盖 [02](02-requirements.md) 交付基线 M4 的"H5 审批/打卡/公告"（L57）。本文档：

1. **固化端侧现状**（工程约定、页面与接口盘点、构建部署链路）；
2. **登记差距**（[20](20-completion-review.md) 复盘 + 本次代码调研新发现，含功能缺陷）；
3. **分期扩展与补强**（H1–H7：质量基线 → 考勤增强 → 表单附件/财务 → 移动协同（含知识库）→ 消息实时化 → 移动 AI → 真机验收与发布）。

阶段编号 **H1–H7 仅在本文档及其回写章节使用**。移动端 AI 在 [21](21-module-plan-ai.md) §9.2 未决项 3 中原为"默认不纳入"，经评审**现纳入正式分期 H6**（复用 21 号后端能力，不新增后端模块）。

### 1.1 技术栈（实测锁定）

| 项 | 选型 | 备注 |
|---|---|---|
| 框架 | uni-app 3.0.0-alpha（`@dcloudio/*` 3.0.0-alpha-5030120260930001）+ Vue 3.5.13 | `vueVersion: 3`；依赖为 alpha 通道（见 §7 未决项） |
| 构建 | Vite 5.2.8 + `@dcloudio/vite-plugin-uni` + sass | `dev:h5`/`dev:mp-weixin`、`build:h5`/`build:mp-weixin`；dev 5174 代理 `/api`/`/auth`/`/ws` → `:18081` |
| 状态 | 手写 `reactive` 单例（`src/store/user.js`） | 无 pinia/vuex（uni-app 生态按轻量处理） |
| 请求 | `src/utils/request.js` | 信封 `ApiError`、`Authorization` + `clientid` + `X-Request-Id`、幂等 `Idempotency-Key`、401 统一跳登录 |
| 质量 | **无 lint/单测/vue-tsc**；`typecheck` 为空占位脚本 | [20](20-completion-review.md) L58/L110 已登记，本计划 H1 处置 |
| 平台 | H5（hash 路由，base `/`）+ 微信小程序（`appid` 为空待配） | manifest `app-plus`/`mp-alipay` 仅预留 |

### 1.2 现状结论

移动主流程（登录 → 工作台 → 审批发起/办理 → 打卡 → 公告/消息 → 我的）功能闭环，构建部署链路已通（`scripts/build-h5.ps1` → `deploy/h5.Dockerfile` + `h5.nginx.conf` 同源反代 `/api/v1/`、`/auth/`、`/ws` → `127.0.0.1:18082`）。缺口分三类：**质量门禁缺失**（H5-G01–G02）、**功能缺口**（H5-G04–G10：定位/附件/协同/实时/AI）、**验收缺口**（H5-G03：真机验证未做，[19](19-module-plan-release.md) 门槛项）。

## 2. 现状盘点（基线）

### 2.1 工程约定（改动必须遵守）

| 约定 | 落点 | 说明 |
|---|---|---|
| 页面注册 | `src/pages.json` | 15 页 + tabBar 4（工作台/审批/考勤/我的，暂无图标）；页面属性含 `enablePullDownRefresh` |
| API 封装 | `src/api/{auth,workflow,attendance,notice,hr,workbench}.js` | 约 55 个导出；workflow.js 含 6 类快捷申请常量 `APPLY_TYPES`、状态映射 |
| 请求 | `src/utils/request.js` | `VITE_APP_BASE_API` 为空 → 根相对路径（H5 nginx 同源反代）；写操作自动带 `Idempotency-Key`；401 → `redirectToLogin()`（800ms 并发去重）；错误 `uni.showToast(body.msg)` |
| 会话 | `src/utils/auth.js` | token 存 storage（`agentoa_token*`）——**与 PC 内存态口径不同**，见 §5 安全项 |
| 导航 | `src/utils/nav.js` | tab 页 `switchTab` 不带 query → storage 传参（`openApprovalTab/consumeApprovalTab`） |
| 时间 | `src/utils/format.js` | `normalizeDate` 兼容 `yyyy-MM-dd HH:mm:ss`/Java `Date#toString()`/RFC3339，不做时区换算 |
| 路由守卫 | `src/main.js` 全局 mixin `onShow` + `App.vue onLaunch` | 非公开页无 token 兜底跳登录 |
| 权限 | `src/utils/auth.js` `hasPermission/hasRole` | 与后端权限串对齐；页面按权限显隐 |

### 2.2 页面与接口盘点

| 页面 | 功能 | 已接接口 |
|---|---|---|
| login/login | 账密 + 可选图形验证码、强制改密跳转 | `/auth/code`、`/api/v1/auth/login` |
| workbench/index | 问候/未读角标/今日打卡卡、8 快捷入口、管理视角卡、待办 top5、余额 top4、公告 top5、今日日程卡（只读） | `GET /api/v1/report/dashboard/workbench` |
| approval/index | 待办/已办/我发起三页签、分页加载 | `/api/v1/wf/tasks/{todo,done}`、`/wf/instances` |
| approval/apply | 6 快捷类型 + 全部流程目录 | `/wf/launchable`、`/wf/launchable/{id}` |
| approval/apply-form | 动态表单（text/number/select/date/datetime/textarea/readonly/list 明细）、自选审批人、草稿/提交、报销明细合计 | `/wf/forms/{formKey}`、业务单 create/submit、`/wf/generic-requests/launch` |
| approval/detail | 表单快照渲染、流程图（`fetchImage` 带 token 转 base64）、审批历史、同意/拒绝/转办/撤销 | `/wf/instances/{id}(/history)`、`/wf/tasks/{id}/{complete,transfer}`、`/wf/instances/{id}/diagram` |
| attendance/index | 实时时钟、上/下班打卡、今日汇总、快捷入口 | `/attendance/punch`（幂等）、`/punch/today`、`/days/today` |
| attendance/records、balance | 区间记录、假期额度与流水 | `/attendance/punch/records`、`/leaves/balance`、`/leaves/ledger` |
| notice/index、detail | 公告列表/详情/已读 | `/notice/list`、`/notice/{id}(/read)` |
| message/index | 全部/待办/公告/系统页签、已读/删除 | `/messages*` |
| mine/index、profile、password | 菜单入口、档案（本人）、改密 | `/auth/profile`、`/auth/password`、`/hr/employees` |

**预留未用 API**（可直接支撑后续分期）：`hr.updateProfile/getEmployee/employeeChanges`（自助资料编辑）、`workflow.listCategories/listDefinitions/startInstance`（通用流程发起）。

### 2.3 构建与验证（现状）

1. `scripts/build-h5.ps1`：`npm ci` + `npm run build:h5`（仅构建，无校验）。
2. `scripts/start-local.ps1`：全栈本地起（H5 = `http://localhost:18082`）。
3. `scripts/smoke.mjs`（后端契约 318 项）、`scripts/browser-smoke.mjs`（**仅 PC Web**，不覆盖移动端）。
4. **无移动端冒烟/真机记录**（H5-G03）。

## 3. 差距清单

| 编号 | 差距 | 来源 | 影响 |
|---|---|---|---|
| H5-G01 | ~~`typecheck` 为空占位（`node -e "process.exit(0)"`），无 lint/单测~~ **已闭环（H1，见 §7）** | [20](20-completion-review.md) L58/L110 | "绿灯假象"，回归靠人 |
| H5-G02 | ~~无移动端端到端冒烟（browser-smoke 只测 PC）~~ **已闭环（H1，见 §7）** | 调研 | 页面渲染/主流程回归缺失 |
| H5-G03 | **H5 真机验收未做**（定位/相机授权、断网重试） | [19](19-module-plan-release.md) 门槛、[02](02-requirements.md) §12.4 | 阻塞 v1.0 |
| H5-G04 | ~~无定位打卡（AT-04"PC/H5 基础定位或 IP 规则"差距）~~ **已闭环（H2，见 §7）** | [02](02-requirements.md) L273 | 打卡可信度不足 |
| H5-G05 | ~~无附件上传（表单无 file 字段，报销无发票，manifest 声明 CAMERA 未用）~~ **已闭环（H2/H3，见 §7）** | 调研 | 报销移动端不闭环 |
| H5-G06 | ~~**缺陷**：`attendance/index.vue` 快捷入口 `goApply()` 忽略入参，补卡/请假/加班均不预选类型（L67-78 传参 vs L151-153 实现）~~ **已闭环（H1，见 §7）** | 调研 | 交互缺陷 |
| H5-G07 | ~~无通讯录、无独立日程（仅工作台只读卡）、知识库 0 能力、无报销列表/付款查询、消息页签缺"提及"过滤~~ **已闭环（H3/H4/H5，见 §7）** | 调研 | 移动协同不完整 |
| H5-G08 | ~~无实时推送（`VITE_APP_WEBSOCKET=false` 且未接 `/api/v1/auth/ws-ticket`），未读靠拉取~~ **已闭环（H5，见 §7）** | 调研 | 移动端消息时效差 |
| H5-G09 | ~~tabBar 无图标；微信小程序 `appid` 为空；`urlCheck:false`~~ **已闭环（H1，见 §7）** | 调研 | 小程序无法正式发布 |
| H5-G10 | ~~无移动端 AI（[21](21-module-plan-ai.md) §9.2 未决项 3）~~ **已闭环（H6，见 §7）** | 21 | 本计划 H6 纳入 |
| H5-G11 | ~~文档口径偏差：pages 实为 15（[20](20-completion-review.md) 记 14）、组件实为 8（记 7）；[03](03-architecture.md) L38"移动端尚未建立"已过时~~ **已闭环（H1，见 §7）** | 调研 | 文档失真 |

## 4. 分期计划（H1–H7）

### 4.1 H1 质量基线与缺陷修复（P0）

**目标**：真实质量门禁 + 已知缺陷清零 + 移动端回归手段。

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H1-01 | 真实类型/lint 检查 | **移除空 `typecheck` 占位**；接入 `eslint`（vue3 + ts + uni-app 规则）与可行的 `vue-tsc --noEmit`（uni 全局组件以 `@dcloudio/types` 声明；若 vue-tsc 对 uni 模板误报则以 eslint + 手写 d.ts 兜底并在 §7 登记结论）；纳入 `scripts/build-h5.ps1` 与 `verify.yml` |
| H5-H1-02 | 缺陷修复 | `attendance/index.vue` `goApply(type)` 预选申请类型（补卡/请假/加班分别带 `correction/leave/overtime` 跳 `apply-form`）；顺带核对 `apply.vue` 快捷入口一致性 |
| H5-H1-03 | 视觉与元信息 | tabBar 图标（4 tab 图标资源 + 选中态）、`manifest.json` 版本号/名称规范化、小程序 `appid` 配置化（env/构建注入） |
| H5-H1-04 | 移动端冒烟 | 新增 `scripts/h5-smoke.mjs`（Playwright 移动视口/Mobile Emulation，H5 形态）：登录 → 工作台 → 审批列表/详情 → 打卡页 → 公告 → 我的；断言 401 跳登录、无 token 缓存泄露（storage 会话口径见 §5） |
| H5-H1-05 | 文档口径修正 | 回写 [20](20-completion-review.md)/[03](03-architecture.md)/user-manual 的页面数、组件数、目录描述（H5-G11） |

**测试与退出条件**：lint/typecheck 真实生效且全绿；`build:h5` + `build:mp-weixin` 通过；`h5-smoke.mjs` 进 CI；快捷入口预选经真机/模拟器验证。

### 4.2 H2 考勤增强（P0，对接 AT-04/AT-09）

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H2-01 | 定位打卡 | `uni.getLocation` 取经纬度随 `/attendance/punch` 提交（后端 P0 已有定位/IP 规则字段与外勤接口，复用 [13](13-module-plan-attendance.md)）；**拒绝授权**交互（引导开启权限/降级 IP 规则，[01](01-research.md) L51） |
| H5-H2-02 | 外勤拍照 | AT-09 P1：`uni.chooseImage` + 上传 `/api/v1/files`（三重校验口径同 PC 外勤弹窗，[09](09-foundation.md) L218）；照片仅用于外勤凭证 |
| H5-H2-03 | 断网与弱网 | 打卡提交失败重试（幂等键防重复打卡）、离线提示、结果反馈（迟到/早退判定文案） |
| H5-H2-04 | 记录增强 | 打卡记录展示位置/照片缩略/设备来源；余额页联动补卡入口 |

**测试与退出条件**：真机完成授权/拒绝授权/断网重试三场景记录（[02](02-requirements.md) §12.4）；重复提交不产生双打卡（幂等断言）；`smoke.mjs` 若后端有新增契约则同步补充。

### 4.3 H3 表单附件与财务（P0 报销闭环）

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H3-01 | 表单 file 字段 | `apply-form.vue` 渲染器扩展 `file/image` 字段类型：`uni.chooseImage`/`chooseFile` → 上传 → 附件 id 落表单；详情页展示附件（预览/下载走授权接口） |
| H5-H3-02 | 报销发票 | 报销明细支持发票附件与发票号；发票占用/去重冲突（409）可读提示（[14](14-module-plan-finance.md) 口径） |
| H5-H3-03 | 财务查询 | 报销列表/进度（复用 `/api/v1/finance/reimburses`）、付款登记查询（`/finance/payments` 只读） |

**测试与退出条件**：移动端"发起报销（含发票附件）→ 审批 → 进度查询"全流程真机走通；附件类型/大小/魔数校验与 PC 口径一致；越权下载 403。

### 4.4 H4 移动协同（含知识库）（P1）

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H4-01 | 通讯录 | 员工搜索/详情（部门、岗位、联系方式拨号/复制）；复用 `/api/v1/hr/employees`，敏感字段按对象权限过滤 |
| H5-H4-02 | 日程 | 今日日程卡 → 日程列表/详情/新建（复用 `/api/v1/calendar/*`，[17](17-module-plan-collaboration.md)） |
| H5-H4-03 | 知识库 | 空间/目录浏览、文档预览（Markdown/纯文本渲染）、搜索、收藏；预览为只读（编辑留 PC） |
| H5-H4-04 | 任务 | 我的任务列表/状态流转（复用协同任务接口）；`mine` 菜单聚合入口 |
| H5-H4-05 | 自助资料 | 档案页接入 `hr.updateProfile`（可编辑字段白名单），启用预留 API |

**测试与退出条件**：四能力真机走查通过；知识文档越权不可见（服务端过滤，客户端不缓存他人文档）；通讯录敏感字段脱敏口径与 [02](02-requirements.md) 一致。

### 4.5 H5 消息实时化（P1）

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H5-01 | WS 接入 | `/api/v1/auth/ws-ticket` + `/ws`（短时单次 ticket，[03](03-architecture.md) §5.1）；`VITE_APP_WEBSOCKET` 开关；H5 与小程序双端验证 |
| H5-H5-02 | 消息完善 | "提及"页签过滤（`MSG_TYPE_NAME` 已有 MENTION）、未读角标实时、点击跳转对应业务 |
| H5-H5-03 | 保活与补偿 | 切后台回前台补偿拉取；断线退避重连；与拉取双轨去重 |

**测试与退出条件**：消息推送时延记录（≤3s）；断网/切后台恢复用例通过；ticket 重放拒绝（与 `smoke.mjs` 现有票据口径一致）。

### 4.6 H6 移动 AI（P1，原 [21](21-module-plan-ai.md) 未决项 3 转正）

**目标**：移动端获得 AI 对话与知识问答入口，复用 21 号 M2/M3 后端（`/api/v1/ai/*`），不新增后端模块、不重复实现网关。

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H6-01 | AI 对话 | 会话列表 + 流式对话（H5 优先 fetch 流式；**小程序无 fetch 流** → 分段响应/轮询降级，见 §7 未决项）；输入文本、停止生成、重新生成 |
| H5-H6-02 | 知识问答 | 提问 + 流式回答 + 引用列表，点击引用跳 H4 文档预览 |
| H5-H6-03 | 多模态与治理 | 图片输入（vision 模型，≤5 张）；限额超限（429）与能力不匹配（400）可读提示；用量遵循 21 号 `LlmGateway` 统一口径 |
| H5-H6-04 | 入口 | 工作台快捷入口 + `mine` 菜单；按权限显隐（`ai:chat:use`/`ai:qa:use`） |

**测试与退出条件**：H5 真机流式对话可用、引用跳转不越权；小程序端至少可用降级通道；限额/错误提示口径与 PC 一致；移动端不落 Key/敏感内容到 storage。

### 4.7 H7 真机验收与发布（P0，[19](19-module-plan-release.md) 门槛）

| 编号 | 需求 | 说明 |
|---|---|---|
| H5-H7-01 | 设备矩阵 | 团队实际使用的 iOS/Android 版本真机覆盖 + 微信开发者工具/真机预览；记录机型/系统/结果 |
| H5-H7-02 | 授权与网络 | 定位/相机授权、拒绝授权、断网重试全场景验收（[02](02-requirements.md) §12.4） |
| H5-H7-03 | 兼容 | H5：iOS Safari、Android Chrome 当前及上一版；小程序基础库兼容区间声明 |
| H5-H7-04 | 发布链路 | `deploy/h5.nginx.conf` 缓存与 HTTPS/WSS 配置、版本更新提示策略、回滚版本保留 |

**测试与退出条件**：[19](19-module-plan-release.md) L23 门槛"H5 审批、打卡、公告在目标设备上通过"达成且记录归档；缺陷清零后进入 v1.0 发布签字流程。

## 5. 非功能需求（全阶段适用）

| 项 | 要求 |
|---|---|
| 安全 | 移动端 token 存 storage 与 PC 内存态差异**需明示风险**：短有效期 + 过期强登 + 敏感内容（Key/他人数据）不落缓存；退出登录清理全部 `agentoa_token*`；XSS：公告/文档/AI 输出按纯文本或白名单渲染 |
| 性能 | 首屏 ≤2s（4G 弱网 ≤5s）；分包按 tab 分组；图片压缩上传 |
| 兼容 | 平台差异清单（H5/小程序 API 差异：fetch 流、chooseImage、getLocation、WS）逐项登记实现分支 |
| 可观测 | `X-Request-Id` 全链路（已有）；移动端错误 toast 与 `errorCode` 可读映射 |
| 合规 | 定位/相机权限用途说明（隐私协议、小程序用户隐私保护指引） |

## 6. 里程碑与依赖

| 阶段 | 前置 | 交付 | 验收口径 |
|---|---|---|---|
| H1 | 无 | 质量门禁 + 缺陷修复 + h5-smoke | 命令真实生效入 CI，冒烟通过 |
| H2 | H1 | 定位/外勤打卡 | 真机三场景记录 |
| H3 | H1 | 表单附件 + 报销闭环 | 含附件全流程真机通过 |
| H4 | H1 | 通讯录/日程/知识库/任务/自助 | 真机走查 + 越权测试 |
| H5 | H1 | WS 实时消息 | 时延 + 恢复用例 |
| H6 | H1、21 号 M2/M3 | 移动 AI 对话/问答 | 真机流式 + 引用跳转 |
| H7 | H2+H3（P0 项） | 真机验收与发布 | 19 号门槛达成 |

依赖：H2/H3 复用后端已有接口为主（外勤/文件/财务），如有字段新增走 [13](13-module-plan-attendance.md)/[14](14-module-plan-finance.md) 变更清单；H4 依赖 [16](16-module-plan-knowledge.md)/[17](17-module-plan-collaboration.md) 已交付 API；H6 依赖 [21](21-module-plan-ai.md) M2/M3 与 PC 端 `stream.ts` 事件口径对齐。每阶段验收通过才进下一阶段（同 [10](10-module-plan-index.md) 统一完成定义）。

### 6.1 未决项（实现期确认）

1. **小程序 AI 流式通道**：**已定案（2026-10-08）：分段 chunked 承载 SSE 帧**——`utils/stream.js` 双通道：H5 走 `fetch` + ReadableStream；小程序走 `uni.request({ enableChunked: true })` + `onChunkReceived` 收字节段拼帧（事件协议与 PC `stream.ts` 一致：`meta/delta/usage/done/error`）。轮询降级不采用（时延差、语义失真）；WebSocket 承载需新增服务端协议，留待确有需求再评估。分段无重排/丢段风险（TCP 有序，本地缓冲按 `\n\n` 切帧）。
2. **uni-app alpha 依赖**：3.0.0-alpha 通道升级策略与锁版本口径（是否转稳定版）。
3. **小程序 appid 与发布主体**：企业主体与隐私指引由谁配置维护。
4. **APP 端**：`app-plus` 预留是否排期（默认不排，H7 后评估）。
5. **移动端 token 存储**：storage 会话是否缩短有效期/加设备指纹（安全评审输入）。
6. **H5 域名与 HTTPS**：定位能力要求 HTTPS（[03](03-architecture.md) L79），正式域名/证书归属确认。

### 6.2 文档回写清单

H 阶段实施后同步更新：[10](10-module-plan-index.md)（状态块）、[19](19-module-plan-release.md)（H5 验收记录挂接）、[20](20-completion-review.md)（缺口闭环附注）、[09](09-foundation.md)（实施记录）、[02](02-requirements.md)/[03](03-architecture.md) 口径修正（H5-G11）；涉及后端字段/接口变更的回写对应模块计划（13/14/16/17/21）变更清单。

## 7. 实施记录

### 7.1 H1 质量基线与缺陷修复（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| H5-H1-01 | 真实 lint/类型检查 | 移除空 `typecheck` 占位：`typecheck` = `vue-tsc --noEmit`（`tsconfig.json` 开 `allowJs`+`checkJs` 非严格、`@/*` 路径别名、`types/{vue-shim,global}.d.ts` 兜底 `.vue` 导入与 `import.meta.env`/`process`）；`lint:eslint`（eslint 9 flat + `eslint-plugin-vue` essential，uni 平台全局对象入 globals，`eslint.config.mjs`）；纳入 `scripts/build-h5.ps1` 与 `verify.yml`（lint → typecheck → build:h5 → build:mp-weixin）。**vue-tsc 对 uni 模板无误报**（自定义标签按未解析组件放行），未启用 eslint 兜底口径；实测报出并修复 24 处真实问题（goApply 入参丢失、`#ifdef` 双 `const source` 声明、信封 `AnyObject\|ArrayBuffer` 未收窄、动态属性无类型等） |
| H5-H1-02 | 缺陷修复 | `attendance/index.vue` `goApply(type)` 预选申请类型：补卡/请假/加班按 `correction/leave/overtime` 匹配 `processKey`/`businessType` 解析流程定义直跳 `apply-form`，未启用回退发起申请目录（新 `utils/quick-apply.js`，与 `attendance/balance.vue` 补卡入口共用）；与 `approval/apply.vue` `findDefForQuick` 口径一致 |
| H5-H1-03 | 视觉与元信息 | tabBar 4 图标 + 选中态（`src/static/tab/*`，`scripts/gen-h5-tab-icons.mjs` 纯 Node zlib 绘制，可复现）；`manifest.json` 小程序隐私声明（`scope.userLocation`/`scope.camera` 用途文案 + `requiredPrivateInfos: [getLocation]`）；appid 配置化（`scripts/prepare-mp-weixin.mjs` 构建前读 `MP_WEIXIN_APPID`（可选 `MP_WEIXIN_VERSION_NAME/CODE`）注入，`build:mp-weixin` 前置，未设置保持 manifest 现值） |
| H5-H1-04 | 移动端冒烟 | 新增 `scripts/h5-smoke.mjs`（Playwright 移动视口 iPhone 13，msedge/chromium 自适配）：登录 → 工作台 → 审批列表/详情 → 打卡页 → 公告 → 我的 → 退出清理 → 无会话跳登录，**10 项断言全过**；`verify.yml` 增 playwright provision + h5-smoke 步骤 |
| H5-H1-05 | 文档口径修正 | [20](20-completion-review.md)/[03](03-architecture.md)/[10](10-module-plan-index.md) 页面/组件数与目录描述回写（H5-G11）：页面 15 → **25**（H2–H6 新增 10 页）、组件 8 → **9**、`typecheck` 空占位缺口闭环 |

### 7.2 H2 考勤增强 + H3 表单附件与财务（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| H5-H2-01 | 定位打卡 | `utils/location.js`（`getLocation` 统一 `{ok,lng,lat,accuracy,denied,msg}` + `guideEnableLocation` 拒绝授权引导去设置）；上/下班打卡取定位随 `lng/lat/accuracyMeters` 提交，拒绝授权/获取失败降级提示「按 IP 规则判定」不阻塞打卡；外勤打卡必须定位成功，拒绝授权弹引导 |
| H5-H2-02 | 外勤拍照 | 「外勤打卡（拍照）」按钮：`uni.chooseImage`（拍照/相册）→ `POST /api/v1/files`（三重校验口径同 PC）→ `punchType=3` + `photoFileId` + 定位；照片仅作外勤凭证 |
| H5-H2-03 | 断网与弱网 | `api/attendance.punch(data, idempotencyKey)` 支持**复用幂等键**：提交失败保留 `{payload,key}` 与可读错误（含 ATTENDANCE_* 错误码），「重试」按钮复用同一 `Idempotency-Key` 防重复打卡；成功反馈迟到/早退判定文案 |
| H5-H2-04 | 记录增强 | `attendance/records.vue` 增坐标（lng/lat/精度）与现场照片缩略（点开全屏预览）；设备/来源行保留；`attendance/balance.vue` 增「补卡申请」入口（预选 correction）。**后端变更**：`PunchRecordVo` 增 `lng/lat/accuracyMeters/photoFileId`（见 [13](13-module-plan-attendance.md) 变更清单） |
| H5-H3-01 | 表单 file/image 字段 | 渲染器扩展 `file`/`image` 字段类型（顶层 + 明细行）：新组件 `components/attachment-field.vue`（`utils/picker.js` 平台分支：H5 `uni.chooseFile` / 小程序 `uni.chooseMessageFile`，图片走 `uni.chooseImage`）→ 上传 `/api/v1/files` → 值 = sys_file fileId 字符串落表单；详情页（`approval/detail.vue` `renderForm`）识别 file/image 字段与明细内 `invoiceId` 汇总「附件」区，预览（图片转 data URI）/下载走授权接口。**后端变更**（[12](12-module-plan-workflow.md) 变更清单）：`FormSchemaBo.FIELD_TYPES` 白名单增 `file/image`；新增 `GET /api/v1/wf/instances/{id}/attachments/{fileId}/download`（实例可见性（`selectDetail`）+ 表单数据实际引用双重判定，`FlowAttachmentAccess` 纯函数可测）。**PC 侧配套**：FormDesigner 调色板增「附件/图片」、`FormSchema.vue` 渲染上传/下载（上传走 axios 服务带鉴权） |
| H5-H3-02 | 报销发票 | 报销明细（`details` 行）「上传发票图片」：`uni.chooseImage` → 上传 → `POST /api/v1/finance/invoices`（`invoiceType=电子发票`、`invoiceNo`/`amount`/`invoiceDate` 取行内字段）→ 行 `invoiceId` 落表单（BO 既有字段）；查看走 `GET /api/v1/finance/invoices/{id}/download`；占用/去重 409 与校验错误经请求层信封提示可读文案；发票号/金额缺失前置拦截 |
| H5-H3-03 | 财务查询 | 新页 `finance/reimburse.vue`（报销列表/进度，状态机文案 1–7，金额自 formData 解析，点击跳流程详情看进度）与 `finance/payments.vue`（付款登记只读，`fn:payment:list` 权限显隐）；入口入工作台九宫格与 mine 菜单 |

### 7.3 H4 移动协同 + H5 消息实时化（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| H5-H4-01 | 通讯录 | `pages/contacts/index.vue`：姓名搜索 + 分页列表 + 展开详情（手机拨打 `uni.makePhoneCall`/复制、邮箱复制、状态标签）；复用 `GET /api/v1/hr/employees`（employee 角色已授 `hr:employee:list`，敏感字段服务端按 `hr:employee:sensitive` 过滤） |
| H5-H4-02 | 日程 | `pages/calendar/index.vue`：时间区间列表（`scope=self`）、待响应邀请「接受/拒绝」（`myResponse=PENDING`）、`cl:event:add` 权限下新建（标题/起止/地点） |
| H5-H4-03 | 知识库 | `pages/knowledge/{index,documents,preview}.vue`：空间列表 → 文档列表（搜索走 `/documents/search` 命中高亮）→ 只读预览（极简 Markdown 块渲染：标题/列表/引用/代码/粗体，纯文本插值**无 v-html、无 XSS 面**）；收藏/取消收藏（`/favorite`） |
| H5-H4-04 | 任务 | `pages/tasks/index.vue`：状态页签（全部/待办/进行中/已完成）+ 状态流转（`PUT /tasks/{id}/status`，乐观锁 `lockVersion`），`cl:task:edit` 权限显隐操作 |
| H5-H4-05 | 自助资料 | `mine/profile.vue` 增「编辑联系方式」：手机/邮箱白名单自助更新（`PUT /api/v1/hr/profile/self`，启用原预留 API，更正 `hr.js` 旧 `updateProfile` 指向的无效地址） |
| H5-H5-01 | WS 接入 | `utils/realtime.js`：`POST /api/v1/auth/ws-ticket` 短时单次 ticket → `uni.connectSocket` `/ws?ticket=`；H5 按 `location` 推导 ws(s)、小程序走 `VITE_APP_WS_URL`/`VITE_APP_BASE_API` 推导；登录/回前台自动接线（`store/user.applyLogin`、`App.onLaunch/onShow`），登出断开 |
| H5-H5-02 | 消息完善 | 消息中心增「提及」页签（`type=MENTION`）；点击跳转按 `bizType` 分流（NOTICE→公告详情、TASK→我的任务、其余 bizId→流程详情）；未读角标实时：推送仅作触发器、角标以 `GET /messages/unread-count` 拉取为准（双轨去重） |
| H5-H5-03 | 保活与补偿 | 心跳 30s **带应答跟踪**（未应答判定假死、主动断开触发重连）；指数退避重连 1s→…→30s 封顶；`App.onShow`（切后台回前台）触发重连补偿，页面 onShow 拉取兜底（断线期间功能不回退） |

### 7.4 H6 移动 AI（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| H5-H6-01 | AI 对话 | `pages/ai/chat.vue`：会话列表面板（切换/删除）+ 流式对话（打字机）+ **停止生成**（`abort` + `POST /chat/messages/{id}/stop`）+ **重新生成**（保留旧版本）+ 失败消息级提示（错误码可读）；SSE 事件 `meta/delta/usage/done/error` 与 PC `stream.ts` 对齐；内容按纯文本渲染（§5 XSS 口径），标「AI 生成内容仅供参考」 |
| H5-H6-02 | 知识问答 | `pages/ai/qa.vue`：提问 + 流式回答 + 引用列表（标题/片段），点击引用跳 H4 文档预览（document 型）或 `link`；历史问答（`GET /qa/history`） |
| H5-H6-03 | 多模态与治理 | 图片输入（≤5 张、单张 ≤10 MiB、jpg/png/webp 前置预检，与服务端 `AiAttachmentRules` 同口径）→ `POST /ai/chat/attachments` → `attachmentIds` 参与推理；限额 429（含流式中途中断）、能力不匹配 400 等错误信封可读提示；用量走 21 号 `LlmGateway` 统一口径 |
| H5-H6-04 | 入口 | 工作台九宫格 + mine 菜单「AI 助手/知识问答」，按 `ai:chat:use`/`ai:qa:use` 显隐 |

### 7.5 验证证据（2026-10-08）

- 前端三门禁（`agentoa-uniapp`）：`lint:eslint` 退出码 0；`typecheck`（vue-tsc）退出码 0；`build:h5` 与 `build:mp-weixin` 均 DONE。
- 后端 `mvn -pl ruoyi-admin -am verify` **BUILD SUCCESS**：含新增 `FlowAttachmentAccessTest` 7 例、`PunchH2Test#recordsExposeLocationAndPhoto`（attendance 10 例）、agentoa-ai 80 例等全量回归。
- 契约冒烟：`node scripts/smoke.mjs` → **PASS: 495** HTTP/WebSocket checks（分支条件计数，覆盖 M0–M9 与 AI M1–M5）。
- 移动冒烟：`node scripts/h5-smoke.mjs` → **PASS: 10** H5 mobile checks（登录/工作台/审批列表·详情/打卡页/公告/我的/退出清理/无会话跳登录；storage 会话口径与敏感内容断言）。
- PC 回归：`node scripts/browser-smoke.mjs` → PASS（含 C1 断网恢复、C2 时延 1809ms、C3 axe 基线不高于 4/11/12/10/9、workflow/AI 页渲染、内存态会话断言）；PC 四门禁 `lint`/`build:prod`/`typecheck`/`test`（110 例）全绿。
- 迁移链无新增（H2–H6 复用既有接口；`FormSchemaBo` 白名单为代码级放开，无 DDL）。

### 7.6 P0 未包含清单与偏差登记

**P0 未包含（H7 与后续）**

- **H7 真机验收与发布**（设备矩阵/授权与网络场景/兼容/发布链路）：转 [24](24-release-acceptance-checklist.md) 人工执行，19 号门槛不变；
- 真机三场景记录（H2 授权/拒绝授权/断网重试）、小程序真机流式与降级通道验证（H6）：均需真机/微信开发者工具，随 H7 一并出记录；
- axe 基线清零、屏幕阅读器走查：沿用 [22](22-module-plan-frontend.md) §7.6 口径，未纳入本期。

**偏差登记**

1. **越权下载语义**：§4.3「越权下载 403」按对象权限口径实现为 **404**（流程附件下载复用 `selectDetail` 可见性判定（`WF_NOT_FOUND` 404），与知识库/`smoke.mjs` 文件契约一致，防存在性探测）；`/api/v1/files/{id}/download` 属主校验不变（smoke 断言 404）。
2. **H5-H1-01 vue-tsc 兜底口径未启用**：实测 uni 模板无误报，`typecheck` 直接采用 `vue-tsc --noEmit`（`checkJs` 非严格模式），未退回 eslint 兜底；`scripts` 目录随 lint 覆盖但不入 vue-tsc。
3. **`h5-smoke` 页面断言以元素/文案为准**：uni-router 首页 hash 为 `#/`（非 `#/pages/workbench/index`），且非 tab 页无 tabbar，冒烟按元素显隐断言 + 先回首页再点 tab，避免绑定路由形态。
4. **H5-H3-01 PC 表单字段同步交付**：为使 file/image 字段可被设计与 PC 填报，同步补 FormDesigner/FormSchema 支持（超出 23 号纯移动端范围，回写 [12](12-module-plan-workflow.md) 变更清单）。
5. **H5-H2-02 外勤照片可见范围**：打卡照片下载维持属主可见（`/api/v1/files/{id}/download` 属主校验），HR 审阅外勤照片的共享授权未开（与「照片仅用于外勤凭证」一致），如需管理端查阅另行立项。
6. **小程序 `uni.chooseFile` 不存在**：文件选择按平台分支（小程序 `uni.chooseMessageFile`），登记于 §5 兼容清单口径。
