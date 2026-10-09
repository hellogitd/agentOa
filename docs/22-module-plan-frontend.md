# 端侧 1：PC Web 前端（agentoa-frontend）开发计划

> 版本：v1.4（F1/F3/F4 已实施，F4-04 无障碍代码部分交付；F5 已定案——F5-01 关闭、F5-02 转 v1.1、F5-03 已交付）｜日期：2026-10-08｜状态：F1/F3/F4/F5-03 交付（见 §7.1–§7.6），浏览器兼容矩阵待人工验收（[24](24-release-acceptance-checklist.md) 清单），F2 决策 B 冻结
> 关联：[02](02-requirements.md)、[03](03-architecture.md)、[10](10-module-plan-index.md)、[19](19-module-plan-release.md)、[20](20-completion-review.md)、[21](21-module-plan-ai.md)、[23](23-module-plan-h5.md)

## 1. 背景与定位

`agentoa-frontend` 是 AgentOA 的 PC Web 端（plus-ui / RuoYi-Vue-Plus 5.6.2-2.6.2 底座），承载 8 大业务模块与模块 10（AI）的全部管理与作业页面（约 90 个 `.vue` 页面，[20](20-completion-review.md) 口径：OA 业务 48 + 系统 39）。模块 1–8 与模块 10 的功能需求已按 [11](11-module-plan-hr.md)–[18](18-module-plan-reporting.md)、[21](21-module-plan-ai.md) 交付；本文档不重复业务需求，而是：

1. **固化端侧现状**（工程约定、页面盘点、验证体系），作为后续改动的基线；
2. **登记差距**（[20](20-completion-review.md) 复盘 + 本次代码调研新发现）；
3. **分期补强**（F1–F5：工程质量、国际化、实时通知、体验与性能、可选增强），每期给出需求编号、涉及文件与退出条件。

阶段编号 **F1–F5 仅在本文档及其回写章节使用**，与路线图交付阶段 M0–M4、AI 阶段 M1–M5（21 号）无对应关系。

### 1.1 技术栈（实测锁定）

| 项 | 选型 | 备注 |
|---|---|---|
| 框架 | Vue 3.5.30 + vue-router 5.0.3 + pinia 3.0.4 | setup 语法糖，`unplugin-vue-setup-extend-plus` 为 keep-alive 命名 |
| UI | Element Plus 2.13.5（+ icons 2.3.2） | `unplugin-vue-components`/`auto-import` 按需引入 |
| 构建 | Vite 7.3.2 + TypeScript ~5.9.3 + UnoCSS 66.6.6 | `@` → `src`，dev 5173，代理 `/backend` → `:18081` |
| 请求 | axios 1.13.6（`src/utils/request.ts`） | envelope `{code,msg,data,timestamp,requestId}` |
| 其他 | vue-i18n 11.3.0、echarts 6.0.0、vxe-table 4.18.1、vue-quill、vue-cropper、highlight.js | i18n 仅覆盖框架文案（见 FE-G01） |
| 工程 | eslint 9.39.1（flat）、prettier、vitest 4.0.18、vue-tsc ^3.2.5 | scripts：`dev`/`build:prod`/`build:dev`/`preview`/`typecheck`/`test(:watch)`/`lint:eslint(:fix)`/`prettier`（F1 补齐 `typecheck`/`test`） |

### 1.2 现状结论

8 大业务模块 + AI 的 PC 页面功能闭环（全 src 无 TODO/占位页，菜单与视图 81 个 component 全部可映射），`build:prod` + `vue-tsc` + `smoke.mjs`（498 项）+ `browser-smoke.mjs` 常绿。缺口集中在**工程质量与体验**（i18n 窄、通知通道默认关闭、大文件、兼容与无障碍未验收），而非业务功能；其中工程质量基线（单测/类型/lint 门禁、元信息、悬空菜单）已由 **F1（2026-10-07）补齐**（见 §7.1）。

## 2. 现状盘点（基线）

### 2.1 工程约定（改动必须遵守）

| 约定 | 落点 | 说明 |
|---|---|---|
| API 封装 | `src/api/<模块>/index.ts` | 新模块单文件内定义 `ApiEnvelope/PageVo/VO/Form` + 具名函数（`listXxx/getXxx/addXxx/updateXxx/delXxx`）；上游 system/monitor 另有 `types.ts` |
| 页面组织 | `src/views/<业务>/<页面>/index.vue` | `<script setup name="Xxx" lang="ts">`；业务级复用组件放 `views/<业务>/components/` |
| 路由 | `src/router/index.ts` 仅 constantRoutes | 业务路由由后端菜单 `getRouters` 下发，`src/store/modules/permission.ts` `loadView()` 映射 `views/**`，`component` 字段形如 `ai/chat/index` |
| 权限 | `src/directive/permission` | `v-hasPermi`（约 286 处）/`v-hasRoles`；编程式 `$auth.hasPermi*`；权限串 `模块:子模块:动作`（`ai:agent:add`、`wf:definition:list`…） |
| 请求 | `src/utils/request.ts` | Bearer token + `clientid` + `Content-Language`；`code===401` 弹框重登；500/601/其他分别 toast/notification；post/put 500ms 防重复；可选 AES+RSA 报文加密（`VITE_APP_ENCRYPT`） |
| 会话 | `src/utils/auth.ts` | token **仅内存**（刷新即登出），浏览器 storage/cookie 不落 token——**有意安全设计**，`browser-smoke.mjs` 有断言，禁止按缺陷"修复" |
| 流式 | `src/api/ai/stream.ts` | fetch + ReadableStream 手工解析 SSE（事件 `meta/step/delta/usage/done/error`，`AbortController` 停止），供 AI 对话/问答/Copilot/Agent 使用 |
| 全局通知 | `src/utils/sse.ts`、`src/utils/websocket.ts` | `VITE_APP_SSE`/`VITE_APP_WEBSOCKET` 控制（当前均为 `false`），消息中心靠页面拉取 |
| 聊天渲染 | `src/utils/markdown.ts` | AI 输出 markdown 渲染（XSS 口径见 §5） |

### 2.2 页面盘点（views 一级目录）

| 模块 | 页面 | 备注 |
|---|---|---|
| ai | chat、qa、kb、copilot、agent、provider、model、prompt、tool、usage | 21 号 M1–M5 全量 |
| attendance | punch、balance、calendar、group、shift、schedule、report | 含额度批次区块 |
| calendar / task | event、room、mine | 路由 `/collaboration/*` |
| finance | reimburse、invoice、payment、budget、expense-type、report | — |
| hr | employee、dept、post、change、contract | employee 24.7K 单文件（FE-G07） |
| knowledge | space、document、files、search、favorite、trash | favorite 逐 id 串行拉详情（FE-G07） |
| notice | announcement、message、template、schedule | — |
| report | hr、attendance、finance、flow、exports + `components/{ReportChart,StatCards,MetricDocs}` | 组件级复用 |
| workflow | todo、done、mine、cc、delegate、definition、detail、monitor + `components/{ChainNodes,DefinitionWizard,FormDesigner,FormSchema}` | — |
| system / monitor / tool / demo | 上游 RuoYi 全套（user/role/menu/dict/config/oss/tenant/client、cache/online/operlog/logininfor、gen…） | 含 iframe 壳 `monitor/admin`、`monitor/snailjob` |
| 其他 | index（工作台）、login、register、error/401、error/404、redirect | — |

公共组件 22 个目录：`Breadcrumb/DictTag/Editor/FileUpload/Hamburger/IconSelect/iFrame/ImagePreview/ImageUpload/LangSelect/Pagination/ParentView/RightToolbar/RoleSelect/RuoYiDoc/RuoYiGit/Screenfull/SizeSelect/SvgIcon/TopNav/UserSelect` 等。

### 2.3 验证体系（现状）

1. `scripts/build-frontend.ps1`：`npm ci --ignore-scripts` → `npm run lint:eslint` → `npm run build:prod` → `npm run typecheck` → `npm run test`（CI `.github/workflows/verify.yml` 同口径，F1 起四门禁）。
2. `scripts/smoke.mjs`：后端 HTTP/WS 契约 498 项检查（含 AI M1–M5）。
3. `scripts/browser-smoke.mjs`：Edge 无头浏览器登录 → 工作台 → 断网/恢复实时通道降级（C1）→ 侧边栏逐页渲染（考勤/财务/公告/知识/协同/AI/流程待办·已办·详情）→ 消息端到端时延实测（C2）→ axe-core 无障碍初筛 5 页出报告（C3，`artifacts/a11y-report.json`）→ 内存会话与无敏感缓存断言。
4. 前端单元测试：vitest 4.0.18 + `vitest.config.ts`，`src/**/__tests__/` 7 个用例文件 110 例（F1 起，纯函数/工具层，随 §7.5/§7.6 扩充，见 FE-F1-02）。

## 3. 差距清单

| 编号 | 差距 | 来源 | 影响 |
|---|---|---|---|
| FE-G01 | i18n 仅覆盖 `route/login/register/navbar` 4 组 key，业务页面全硬编码中文 | 调研 | 多语言能力名存实亡；`Content-Language` 协议已通但无词条 |
| FE-G02 | ~~vitest 闲置、零用例，`tsconfig` include 的 `vitest.config.ts` 不存在~~ **已闭环（F1）** | [20](20-completion-review.md) L109 | 已建立纯函数/权限过滤/信封分支回归防线（77 例） |
| FE-G03 | ~~无 `typecheck` npm script，类型检查藏在 `build-frontend.ps1`/CI~~ **已闭环（F1）** | [20](20-completion-review.md) L97 | `npm run typecheck` 已入本地脚本与 CI，lint/test 一并入门禁 |
| FE-G04 | ~~悬空菜单：`sys_menu` 132「字典数据」component=`system/dict/data` 无对应视图~~ **已闭环（F1，`V33` 移除）** | 调研 | 全库菜单 99 个 component 复核后唯一悬空项已清；上游遗留菜单（`workflow/leave/leaveEdit`、`workflow/spel`）确认不在 Flyway 种子 |
| FE-G05 | ~~`VITE_APP_SSE=false`、`VITE_APP_WEBSOCKET=false`，消息/待办靠拉取~~ **部分闭环（F3）** | 调研 | 站内 ticket WS 通道已全局接入（`utils/realtime`）：未读角标推送 + 断线退避重连 + 降级拉取；上游 SSE/WS 通道仍按部署配置默认关闭（见 §7.3 开关矩阵） |
| FE-G06 | ~~`package.json` name/version 仍是 `ruoyi-vue-plus 5.6.2-2.6.2`；`agentoa-frontend/README.md` 端口说明过时~~ **已闭环（F1）** | 调研 | name=`agentoa-frontend`/version=`1.0.0`，README 重写（端口 5173/18080、命令与目录约定） |
| FE-G07 | ~~大文件：`hr/employee` 24.7K、`system/dict` 23.7K、`ai/kb` 20.9K；`knowledge/favorite` 逐 id 串行请求（N+1）~~ **已闭环（F4，2026-10-08）** | 调研 | 三个大文件拆为子组件（`hr/employee` 4 个、`ai/kb` 3 个、`system/dict` 2 个，keep-alive name 与路由 component 不变）；N+1 走后端批量接口 `GET /api/v1/knowledge/documents/batch`（见 [16](16-module-plan-knowledge.md) 变更清单） |
| FE-G08 | 浏览器兼容矩阵与无障碍关键路径未验收 | [19](19-module-plan-release.md) 门槛 | 阻塞 v1.0 发布验收；无障碍**代码侧**已补（登录/待办/审批表单键盘可达，见 §7.5），自动化初筛已接入（axe-core 扫描 5 个关键页出报告，见 §7.6），矩阵与屏幕阅读器走查转 [24](24-release-acceptance-checklist.md) 人工执行 |

## 4. 分期计划（F1–F5）

### 4.1 F1 工程质量基线（P0）

**目标**：本地与 CI 具备一致的类型、测试、lint 门禁，交付物元信息可信。

| 编号 | 需求 | 说明 |
|---|---|---|
| FE-F1-01 | typecheck 脚本 | `package.json` 增 `"typecheck": "vue-tsc --noEmit"`；`build-frontend.ps1` 与 `verify.yml` 改调 `npm run typecheck`（先 build 生成 auto-import 声明再检查的顺序保持不变） |
| FE-F1-02 | vitest 最小用例 | 新增 `vitest.config.ts`（含 `tsconfig` 既有 include）+ `npm run test`；首批用例：`utils/ruoyi.ts`（parseTime/handleTree/tansParams）、`utils/index.ts`、`request.ts` 信封分支（200/401/500/601/blob）、`store/modules/permission.ts` 菜单过滤与 `loadView` 映射；不追求覆盖率指标，先建立回归防线 |
| FE-F1-03 | lint 门禁 | `verify.yml` 增 `npm run lint:eslint`（存量告警先清零再强制） |
| FE-F1-04 | 元信息与文档 | `package.json` name/version 改为 `agentoa-frontend` + 项目版本；重写 `agentoa-frontend/README.md`（真实端口 5173/18080、命令清单、目录约定、指向 docs/22） |
| FE-F1-05 | 菜单/路由清理 | 修复或移除 `system/dict/data` 悬空菜单（迁移方式：新增隐藏修正迁移，不改已执行迁移）；核对上游遗留菜单（如参考脚本中的 `workflow/leave/leaveEdit`、`workflow/spel`）不在 Flyway 种子中即可，登记结论 |

**测试与退出条件**：`npm run typecheck / test / lint:eslint` 全绿并纳入 `verify.yml`；`build:prod` + `smoke.mjs` + `browser-smoke.mjs` 回归通过；悬空菜单消除。退出条件达成才进 F2。

### 4.2 F2 国际化（P0 决策，P1 落地）

| 编号 | 需求 | 说明 |
|---|---|---|
| FE-F2-01 | 策略决策 | 二选一（见 §7 未决项）：A. 业务文案全量接入 vue-i18n（zh_CN/en_US）；B. 明确"业务仅中文、框架文案双语"，本文档回写结论并冻结 |
| FE-F2-02 | 词条接入（若选 A） | 按模块抽离 `src/lang/zh_CN.ts`/`en_US.ts` 业务 key（`ai.*`/`at.*`/`wf.*`…），菜单标题走后端 `Accept-Language`，字典标签复用 `DictTag` |
| FE-F2-03 | 格式本地化 | 时间（`parseTime`）、金额、数字千分位随 locale 渲染 |

**测试与退出条件**（若选 A）：切换 zh_CN/en_US 全站无硬编码漏出（脚本扫描中文字符 + 抽样走查）；（若选 B）决策记录在本文档 §7 与 [20](20-completion-review.md) 附注。

### 4.3 F3 通知实时化（P1）

| 编号 | 需求 | 说明 |
|---|---|---|
| FE-F3-01 | 通道启用评估 | 评估 `VITE_APP_SSE`/`VITE_APP_WEBSOCKET` 默认开启的条件（生产 HTTPS/WSS、`/resource/sse` 与 `/ws` ticket 鉴权口径），给出开关矩阵 |
| FE-F3-02 | 实时角标 | 站内信/待办未读实时更新（`layout/components/notice` + `store/notice`），与页面拉取双轨去重 |
| FE-F3-03 | 连接状态 | 工作台"实时连接"指示（已有雏形）扩展为断线提示 + 自动重连 + 退避；断线期间降级为拉取 |

**测试与退出条件**：消息端到端时延记录（≤3s）；断网/恢复场景 `browser-smoke.mjs` 扩展用例通过；SSE/WS 关闭时功能不回退（拉取兜底）。

### 4.4 F4 体验与性能（P1）

| 编号 | 需求 | 说明 |
|---|---|---|
| FE-F4-01 | 大文件拆分 | `hr/employee`、`system/dict`、`ai/kb` 拆为子组件/组合式函数，保持 keep-alive name 与路由 component 不变 |
| FE-F4-02 | N+1 治理 | `knowledge/favorite` 改批量详情（后端如无批量接口则登记为后端变更，走 [16](16-module-plan-knowledge.md) 变更清单） |
| FE-F4-03 | 首屏与包体 | 路由懒加载核查、echarts 按需引入、`dist` 体积基线记录（gzip 分包对比） |
| FE-F4-04 | 兼容与无障碍 | 按 [02](02-requirements.md) §12.4 执行浏览器矩阵（Chrome/Edge/Firefox 最近两稳定版、Safari 当前及上一版）+ 无障碍关键路径（登录/待办/审批表单键盘可达），输出验收记录（[19](19-module-plan-release.md) 门槛）｜**代码侧键盘可达已交付（§7.5），矩阵验收待人工** |

**测试与退出条件**：拆分后 `build:prod`+`vue-tsc`+`browser-smoke` 全绿；包体/首屏基线归档；兼容矩阵与无障碍记录进入发布验收包。

### 4.5 F5 可选增强（2026-10-08 定案）

| 编号 | 需求 | 定案 |
|---|---|---|
| FE-F5-01 | 聊天 UI 组件 | **关闭（不引入 element-plus-x）**：自研 chat 页已覆盖流式/停止/重生成/附件/Agent 轨迹/模板切换且 XSS 白名单刚加固，第三方组件需重做安全审定且无 Agent 轨迹能力（见 [21](21-module-plan-ai.md) §9.2-2） |
| FE-F5-02 | 报表看板增强 | **转 v1.1 需求池**（图表联动、口径下钻、导出模板扩展，与 [18](18-module-plan-reporting.md) P1 对齐）；本期不实施 |
| FE-F5-03 | 编辑器安全加固 | **已交付（最小加固，不做编辑器替换）**：粘贴 HTML 标签/属性白名单 + `safeUrl` 协议清洗（新增 `utils/sanitize.ts` 共享纯函数）、图片粘贴/插入统一走 sys_file 上传（`checkEditorImage` MIME+大小预检，禁 `data:` 内联），见 §7.6 |

**测试与退出条件**：F5-03 以 vitest 清洗用例 + 四门禁回归为退出条件（已达成）；F5-02 立项时另行定义。

## 5. 非功能需求（全阶段适用）

| 项 | 要求 |
|---|---|
| 安全 | token 内存态不回退；AI markdown 渲染防 XSS（白名单标签/转义）；文件下载走授权接口；`VITE_APP_ENCRYPT` 口径与后端一致 |
| 性能 | 列表页首屏 ≤2s（本地）；路由懒加载按菜单粒度分包；大表格（vxe-table）虚拟滚动按需 |
| 兼容 | 见 FE-F4-04 矩阵；分辨率 1280×720–3840×2160 自适应（[02](02-requirements.md) §12.4） |
| 可观测 | `X-Request-Id` 全链路；前端错误提示与 `errorCode`（[05](05-api-spec.md) §错误）可读文案映射 |
| 工程 | `build:prod` + `typecheck` + `test` + `lint` 为合并门禁；依赖升级走 lockfile 评审 |

## 6. 里程碑与依赖

| 阶段 | 前置 | 交付 | 验收口径 |
|---|---|---|---|
| F1 | 无 | typecheck/test/lint 门禁 + 元信息修正 + 菜单清理 | 四命令全绿入 CI，`browser-smoke` 回归通过 |
| F2 | F1 | i18n 决策（+ 词条接入若选 A） | 决策文档化或双语走查通过 |
| F3 | F1 | 通知实时化 | 时延记录 + 断线降级用例 |
| F4 | F1 | 拆分/性能/兼容/无障碍 | 基线归档 + 验收记录 |
| F5 | 任意 | 可选增强 | 逐项定义 |

依赖：F3 依赖后端 outbox/WS/SSE 通道（M0 既有）；F4 的 FE-F4-02 可能引入 [16](16-module-plan-knowledge.md) 后端变更；F5-01 依赖 [21](21-module-plan-ai.md)。每阶段验收通过才进下一阶段（同 [10](10-module-plan-index.md) 统一完成定义）。

### 6.1 未决项（实现期确认）

1. **i18n 策略**：**已定（2026-10-07）：B. 业务仅中文、框架文案双语**——`src/lang` 保持 `route/login/register/navbar` 4 组 key，业务页面不接入 vue-i18n，`Content-Language` 协议不变；后续若启动多语言需求再单独立项（届时按 FE-F2-02 走 A 方案抽词条）。
2. **通知通道默认值**：**已定（2026-10-07）：保留按部署配置**——`VITE_APP_SSE`/`VITE_APP_WEBSOCKET` 默认仍为 `false`，生产是否开启由部署环境变量决定；开关矩阵与 HTTPS/WSS、ticket 鉴权口径留待 F3 立项时补充。
3. **vitest 范围**：**已定（2026-10-07）：仅纯函数/工具层**——不引入 `@vue/test-utils`，组件挂载测试待 F4 之后按需立项。
4. **聊天 UI 组件**：**已定（2026-10-08）：不引入 element-plus-x，FE-F5-01 关闭**——维持自研轻量组件，理由与后续评估口径见 §4.5。

### 6.2 文档回写清单

F 阶段实施后同步更新：[10](10-module-plan-index.md)（状态块）、[20](20-completion-review.md)（缺口闭环附注）、[09](09-foundation.md)（实施记录），涉及后端变更的回写对应模块计划（11–18、21）变更清单。

## 7. 实施记录

### 7.1 F1 工程质量基线（2026-10-07）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| FE-F1-01 | `typecheck` 脚本 | `package.json` 增 `"typecheck": "vue-tsc --noEmit"`；`scripts/build-frontend.ps1` 与 `.github/workflows/verify.yml` 均改调 `npm run typecheck`（保持 build 先于 typecheck，auto-import 声明由 vite 生成） |
| FE-F1-02 | vitest 最小用例 | 新增 `vitest.config.ts`（`environment: node`、`include: src/**/__tests__/**/*.test.ts`、复用 `vite/plugins/auto-import` 保持自动导入一致）+ `npm run test`/`test:watch`；4 个用例文件 77 例：`src/utils/__tests__/ruoyi.test.ts`（parseTime/addDateRange/handleTree/tansParams/selectDictLabel(s)/parseStrEmpty/mergeRecursive/getNormalPath/blobValidate）、`src/utils/__tests__/index.test.ts`（formatDate/formatTime/getQueryObject/byteLength/cleanArray/param/param2Obj/objectMerge/class helpers/debounce/deepClone/uniqueArr/createUniqueString/isExternal）、`src/utils/__tests__/request.test.ts`（信封 200/401/500/601/其它码/blob 六分支 + 传输层错误文案映射 + 500ms 重复提交拦截 + 参数序列化与请求头）、`src/store/modules/__tests__/permission.test.ts`（`filterDynamicRoutes` 权限/角色过滤、`loadView` 映射与悬空组件解析为 undefined、store 状态存取） |
| FE-F1-03 | lint 门禁 | 存量 194 项清零（193 项 prettier 自动修复 + `src/layout/components/TopBar/index.vue` 补 `lang="ts"` 并按 `Sidebar/index.vue` 约定改用 `getSidebarRoutes()` + `computed<RouteRecordRaw[]>`、`parseInt`→`Math.floor`）；`verify.yml` 与 `build-frontend.ps1` 增 `npm run lint:eslint` |
| FE-F1-04 | 元信息与文档 | `package.json` name=`agentoa-frontend`、version=`1.0.0`（对齐 `agentoa-uniapp`）、description/author 纠偏、移除指向 plus-ui 的 repository；重写 `agentoa-frontend/README.md`（真实端口 5173/18080、命令清单、目录约定、token 内存态安全口径、指向本文档） |
| FE-F1-05 | 菜单/路由清理 | 新增隐藏修正迁移 `V33__remove_dangling_dict_data_menu.sql`（`agentoa-foundation`）删除 `sys_menu` 132「字典数据」（component=`system/dict/data` 无对应视图）及其 `sys_role_menu` 绑定，不改已执行迁移；核对结论：`workflow/leave/leaveEdit`、`workflow/spel` **不在任何 Flyway 种子中**（仅存于 `upstream-reference` 与非 Flyway 的 `script/sql/update_*.sql`），无需清理 |

**验证证据**

- `npm run lint:eslint` 退出码 0（原 194 error）；`npm run test` 4 文件 77 例全绿；`npm run build:prod` 通过（21.6s）；`npm run typecheck` 退出码 0。
- 后端 `mvn -f agentoa-backend/pom.xml -pl ruoyi-admin -am verify` BUILD SUCCESS（含 agentoa-ai 66 例），V33 迁移不影响双向回滚门禁（`MigrationReversibilityTest` 仅锁定 workflow V23–V27 / notice 具名迁移，foundation V1–V3 本就无回滚脚本）。
- 全库菜单组件核对：`sys_menu` 99 个 component 中 98 个可映射到 `src/views/**`，唯一悬空项即 menu 132，已由 V33 移除；前端全库无 `dict-data` 路由引用（字典数据由 `system/dict/index` 内嵌管理），删除安全。
- 回归：`smoke.mjs` / `browser-smoke.mjs` 未改动；`verify.yml` 四门禁顺序为 lint → build → typecheck → test，本地 `scripts/build-frontend.ps1` 同口径。

**P0 未包含清单（F1 退出条件之外）**

- F2 词条接入（策略已定 B，仅框架文案双语，业务中文不接入 vue-i18n）；
- F3 通知实时化（通道保留按部署配置，默认 `false`；实时角标/连接状态/断线降级未做）；
- F4 大文件拆分（`hr/employee` 24.7K、`system/dict` 23.7K、`ai/kb` 20.9K）、`knowledge/favorite` N+1（需后端批量详情接口，见 [16](16-module-plan-knowledge.md) 变更清单）、首屏与包体基线、浏览器矩阵与无障碍验收；
- F5 可选增强（element-plus-x 聊天组件、报表联动、编辑器评估）；
- uniapp `typecheck` 空脚本治理（[23](23-module-plan-h5.md) 范围）。

**实现期偏差登记**

- `sprintf`（`src/utils/ruoyi.ts`）全库无调用且实现存在 `arguments` 遮蔽缺陷（上游遗留），未纳入用例、未改生产代码；`debounce` 尾沿调用因同名参数遮蔽不透传参数（上游遗留），用例按现状锁定合并次数而非参数。
- 测试用例落 `src/**/__tests__/`（沿用 `tsconfig.json` 既有 `exclude` 约定），该目录不纳入 `vue-tsc` 检查。

### 7.2 F1 验收回归暴露的缺陷修复（2026-10-07）

F1 退出条件要求 `smoke.mjs` + `browser-smoke.mjs` 回归通过。实际执行时暴露了一批此前被"AI 段从未跑绿"掩盖的缺陷，分两类修复（均为契约修正，非功能扩展）：

**冒烟脚本自身缺陷（`scripts/smoke.mjs`）**

| 缺陷 | 修复 |
|---|---|
| AI 段自建用户未完成强制改密握手，被 `AccountGuard` 以 `PASSWORD_CHANGE_REQUIRED` 403 拦截（`makeUser` 助手有这一步，AI 段内联创建漏掉） | 改用 `makeUser('ai',[m10RoleId])`，并把遗留的 `m10Login.user.userId` 引用统一为 `m10Account.userId` |
| 消息投递轮询窗 30s，小于 outbox 排空时间（调度器固定 20 条/3s，财务段单次突发约 200 条事件） | 4 处消息轮询窗统一提到 90s（断言不变，仅放宽截止时间） |
| 会议室列表按 id 升序、默认 10 条/页，重复运行累积 11 个会议室后新记录落到第二页 | 改用接口原生 `keyword` 按名精确过滤 |
| 工具码固定 `smoke_tool`/`smoke_mcp`，上次失败残留导致 409 | 工具码加时间戳后缀（同会议室手法） |
| SSE 事件字面量断言 `'event: meta'`，与 Spring `SseEmitter` 实际输出 `event:meta` 不符（SSE 规范允许冒号后无空格，前端解析器 `slice(6).trim()` 两者皆可） | 6 处断言改为 `/event:\s*meta/`、`/event:\s*error/` 容忍空白 |
| M3 段 `EMBEDDING_MODEL_UNAVAILABLE`(400) 与来源 404 未向 `call()` 传 `status:`，被默认 HTTP 200 断言误判 | 补 `status:400`/`status:404` |

**后端契约缺陷**（详见 [21](21-module-plan-ai.md) §9.2「契约回归暴露项」）：SSE 响应类型与 ASYNC 分派信封污染、`chat/templates` 场景模板过滤、`GET /kb`/`GET /models` 无过滤 NPE、MCP 建连异常归一化、检索管线 embedding 调用顺序、会话绑定模型失效后回退默认模型。另修一处跨模块缺陷：`WorkflowOrchestrator.applyTaskSnapshot` 的 `current_assignees`（展示用字段）拼接超过 `VARCHAR(512)` 即 500（同角色 26+ 名成员即触发），改为按 500 字符截断。

**前端页面缺陷（2026-10-07 追加）**

| 缺陷 | 修复 |
|---|---|
| `ai/chat` 选中历史会话时无条件采用会话绑定的 `modelId`，模型被删除后发送请求即 404 `AI_MODEL_NOT_FOUND`（会话从此不可用） | `selectConversation` 仅在该 `modelId` 仍存在于启用模型列表时采用，否则保留当前选择（`loadModels` 已置全局默认）；下一次发送会把会话绑定改写为实际使用模型，自愈历史数据 |

**验收证据（2026-10-07）**

- `node scripts/smoke.mjs` → **PASS: 482 HTTP/WebSocket checks**（覆盖 M0–M9 与 AI M1–M5 全契约链）。
- `node scripts/browser-smoke.mjs` → PASS（登录、工作台、WebSocket、流程/财务/公告/知识/协同/AI 各页渲染、内存态会话与刷新登出断言）。
- 后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（含 agentoa-ai 69 例：66 + 新增 3 例回归——列表无过滤 ×2、会话绑定失效模型回退 ×1）。
- 前端四门禁：`lint:eslint` 退出码 0、`test` 77 例全绿、`build:prod` 24s、`typecheck` 退出码 0。
- 迁移链 V28→V33 在 MySQL 8.4 真实执行（`flyway_schema_history` 全部 `success=1`，menu 132 已删除）。

### 7.3 F3 通知实时化（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| FE-F3-01 | 通道开关矩阵 | **站内 ticket WS（`/backend/ws?ticket=`，[M0 outbox/WS](09-foundation.md) 通道）为默认实时通道**，登录后全局接入（不受 `VITE_APP_*` 控制，与工作台原有口径一致）；**上游 plus-ui 通道** `/resource/sse`、`/resource/websocket` 保留按部署配置（`VITE_APP_SSE`/`VITE_APP_WEBSOCKET` 默认 `false`，开启需生产 HTTPS/WSS 与 ticket 鉴权就绪）。两路消息统一经 notice store 收口 |
| FE-F3-02 | 实时未读角标（推送+拉取双轨去重） | 重写 `store/modules/notice.ts` 为通知中枢：推送事件（`{messageId,type,title}`）按 `messageId` 去重入列（上限 50 条）并触发未读刷新；**未读角标始终以 `GET /api/v1/notice/messages/unread-count` 拉取为准**，推送仅作触发器，双轨不重复计数；`layout/components/notice/index.vue` 重写为铃铛 + `el-badge` 下拉面板（移除上游「前往 gitee」死链接），接入 `Navbar.vue`；消息中心页（`views/notice/message`）与下拉面板共用 store 角标，标已读/全读/删除后同步刷新 |
| FE-F3-03 | 连接状态 + 退避重连 + 断线降级 | 新增 `utils/realtime.ts`（ticket WS 统一收口：换票 → 心跳 30s → 指数退避重连 1s→2s→4s→…→30s 封顶）；连接状态（`connecting/connected/disconnected`）上报 store，**断线期间 store 启动 60s 定时拉取兜底**；工作台（`views/index.vue`）删除自建 WS 代码，连接指示改读 store 状态（实时连接/连接中/断线重连中（已降级定时拉取）/定时刷新），推送到达联动刷新消息表；登出调用 `disconnectRealtime()` + 清空通知态 |

**测试与退出条件核对**：断网/恢复由退避重连与 30s 工作台拉取双轨覆盖（SSE/WS 关闭时功能不回退，拉取兜底）；消息端到端时延 ≤3s 由 outbox 排空节奏（20 条/3s）+ WS 即时推送保证；`build:prod` + `vue-tsc` + `lint` + vitest 77 例全绿，`browser-smoke.mjs` 回归通过（WebSocket 段覆盖）。

**P0 未包含清单**：~~断网恢复的 `browser-smoke.mjs` 专项用例~~与~~消息端到端时延实测记录~~已由 C1/C2 交付（2026-10-08，见 §7.6）；遗留：时延实测为单次采样，多轮统计与 3s 目标口径说明见 [24](24-release-acceptance-checklist.md) §3。

### 7.4 F4 体验与性能（2026-10-08，部分交付）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| FE-F4-01 | 大文件拆分 | `hr/employee/index.vue`（24.8K→14K）拆出 `components/{EmployeeFormDialog,EmployeeLifecycleDialogs,EmployeeImportDialog,EmployeeDetailDrawer}`；`ai/kb/index.vue`（21K→12K）拆出 `components/{KbMemberDialog,KbSourceDialog,KbSearchTestDialog}`；`system/dict/index.vue`（23.7K→13K）拆出 `components/{DictTypeDialog,DictDataPanel}`（数据面板含字典数据 CRUD + 弹窗，按 `dict`/`selectToken` 驱动）；keep-alive name（`HrEmployee`/`AiKb`/`Dict`）与路由 component 不变 |
| FE-F4-02 | N+1 治理 | 后端新增 `GET /api/v1/knowledge/documents/batch?ids=`（`KnowledgeDocumentService.details`：`selectBatchIds` + 服务端授权过滤，无权限/已删除静默丢弃，上限 200）；前端 `knowledge/favorite` 改为一次批量拉取（原逐 id 串行 `getDocument`）；回归用例 `KnowledgeDocumentH2Test#batchDetailsFilterByAuthorizationAndDropDeleted` |
| FE-F4-03 | 首屏与包体 | echarts 按需引入：新增 `utils/echarts.ts`（core + Line/Bar/Pie/Gauge + Title/Tooltip/Legend/Grid + Canvas），`monitor/cache` 与 `report/components/ReportChart` 改用之（原 `import * as echarts` 全量）；路由懒加载核查确认 `import.meta.glob` 按菜单粒度分包不变；**dist 体积基线（2026-10-08）**：336 资源共 5.5MB，JS 3.87MB / CSS 640KB，最大分包 `index-*.js` 1.65MB（gzip 553KB）、独立 `echarts-*.js` 556KB（gzip 188KB，按需后从主包拆出，仅报表/监控页加载） |
| FE-F4-04 | 兼容与无障碍 | **未做**：浏览器矩阵（Chrome/Edge/Firefox 最近两稳定版、Safari 当前及上一版）与无障碍关键路径（登录/待办/审批表单键盘可达）需人工验收，属 [19](19-module-plan-release.md) 发布门槛 |

**验证证据（2026-10-08）**：`mvn verify` BUILD SUCCESS（knowledge 33 例含新增批量详情回归）；前端四门禁 `lint:eslint`/`build:prod`（22.6s）/`typecheck`/`test`（77 例）全绿；`smoke.mjs` **493** 项 PASS；`browser-smoke.mjs` PASS（覆盖拆分后 hr/dict/kb 页面渲染）。

### 7.5 F4-04 无障碍 + §5 XSS 加固 + AI-M2-04 上传预检（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| FE-F4-04（代码侧） | 无障碍关键路径键盘可达 | `views/login.vue`：验证码刷新由纯 `img @click` 改为原生 `<button type="button">`（Enter/Space 可触发，`aria-label`「看不清？点击刷新验证码」、`alt` 改为描述图片本身）、4 组输入框补 `aria-label`、进入页面自动聚焦账号框、刷新控件加 `:focus-visible` 轮廓；`views/workflow/todo/index.vue`：标题 `el-link` 无 `href` 时不可聚焦，补 `role="link"` + `tabindex="0"` + `@keydown.enter` + `aria-label`；转办/加签只读选人框补 `@keydown.enter/@keydown.space` 触发选人（原仅 `@click`，键盘无法打开）+ `aria-label`；`views/workflow/components/FormSchema.vue`：明细表格单元格输入框补 `aria-label`（`${字段} - ${明细项}`）；`views/workflow/detail/index.vue`：流程图 `el-image` 补 `alt` |
| FE-G09（新增，§5 安全红线） | AI markdown 渲染防 XSS 补协议白名单 | `utils/markdown.ts` 原为「先转义再合成标签」，属性引号已转义故无属性逃逸，但链接/图片 URL 无 scheme 白名单，`[x](javascript:...)`/`![x](javascript:...)` 可产出可点击/可加载面。新增导出 `safeUrl(raw, {allowDataImage})`：仅放行 `http/https/mailto/tel` + 相对路径/锚点 + 图片侧栅格 `data:image/*`（不含 `svg+xml`）；先抹 C0 控制符与空白再判协议（防 `java\nscript:`、`%6aavascript:` 编码伪装）；不放行时降级为纯文本（保留可读内容，不产出可点击面） |
| AI-M2-04（前端侧） | 聊天图片上传预检 | 新增 `utils/upload.ts` `checkChatImage`（张数 ≤5、单张 ≤10 MiB、扩展名 jpg/jpeg/png/webp，与服务端 `AiAttachmentRules` 同口径）；`views/ai/chat/index.vue` 接 `before-upload` 拦截 + `handleUpload` 补失败分支（原仅 `onSuccess`，请求失败无提示）。内容魔数与扩展名一致性仍由服务端三重校验兜底，前端不重复读字节 |
| 回归 | vitest 补纯函数用例 | 新增 `src/utils/__tests__/markdown.test.ts`（14 例：协议白名单 6 组 + XSS 渲染 6 组 + diffLines 2 组）与 `src/utils/__tests__/upload.test.ts`（6 例）；用例总数 **77 → 97** |

**Element Plus 属性透传核查（避免写入被静默丢弃的属性）**：`el-input`（`inheritAttrs:false`，仅显式转发 `tabindex`/`aria-label`）、`el-image`（`imgAttrs` 透传除 `class`/`style`/监听器外全部属性）、`el-link`/`el-button`（无 `inheritAttrs:false`，fallthrough 到根元素）均已按实测行为选用属性；`el-input` 上不写 `aria-haspopup`（会被丢弃），`el-date-picker` 不写 `aria-label`（未确认转发到内层 input）。

**验证证据（2026-10-08）**

- 前端四门禁：`lint:eslint` 退出码 0；`build:prod` 23.96s；`typecheck` 退出码 0；`test` **97 例**全绿（6 文件）。
- 回归：`smoke.mjs` **493** 项 PASS；`browser-smoke.mjs` PASS（覆盖登录页、workflow 待办/详情、AI 聊天页渲染）。
- 测试断言覆盖的攻击样例：`javascript:` / 大小写混淆 / `java\nscript:`、`java\tscript:`、`java\rscript:`、前导空白 / `%6aavascript:` / `vbscript:` / `data:text/html` / `data:image/svg+xml` / 属性引号闭合；正常链接、相对路径、锚点、`data:image/png;base64` 均不回归。

**P0 未包含清单**

- 浏览器兼容矩阵（Chrome/Edge/Firefox 最近两稳定版、Safari 当前及上一版）与无障碍人工验收记录（[19](19-module-plan-release.md) 发布门槛）——**已转 [24](24-release-acceptance-checklist.md) 人工验收清单**（矩阵表 + 键盘复核 + 签署栏），自动化初筛（axe-core）已由 §7.6 接入；
- 屏幕阅读器完整走查（本轮只做键盘可达 + 可及名称，未做 ARIA 全量审计）——同转 [24](24-release-acceptance-checklist.md) §2.2（NVDA/VoiceOver 五路径走查表）；
- `views/knowledge/search` 的 `hit.highlight` `v-html` 已由后端 `MarkdownText.highlight` 转义（`&<>`）后输出 `<em>` 标签，本轮复核确认无注入面，未改动。

### 7.6 F5 定案 + F5-03 编辑器加固 + 验证体系补强（2026-10-08）

**交付项**

| 编号 | 交付 | 落点 |
|---|---|---|
| FE-F5-03 | 富文本粘贴清洗 | 新增 `utils/sanitize.ts` `sanitizeRichHtml`（标签/属性白名单 + `on*` 拒绝 + `safeUrl` 协议白名单 + style 声明过滤，script/iframe 等连内容丢弃、白名单外解包保文本）；`components/Editor/index.vue` 注册 Quill 粘贴拦截，`text/html` 先清洗再 `dangerouslyPasteHTML`；受控注入面与 `markdown.ts` 同口径 |
| FE-F5-03 | 图片走 sys_file 上传 | 编辑器图片粘贴（截图等文件）转 `/resource/oss/upload` 上传后按 URL 插入；`utils/upload.ts` 增 `checkEditorImage`（MIME jpg/jpeg/png/webp + 大小，与聊天附件口径一致，去 svg）；`insertImage` 落库前 `safeUrl` 校验；**禁 `data:` 内联**（粘贴 HTML 中 data: 图片整标签丢弃） |
| FE-F5-01/02 | F5 定案 | F5-01 关闭（不引入 element-plus-x）、F5-02 转 v1.1 需求池（见 §4.5） |
| FE-F3 补强 | 实时通道假死检测 | `utils/realtime.ts` 心跳补应答跟踪（服务端回显 HEARTBEAT）：上一次心跳超时未应答即判定连接假死、主动断开触发重连降级——修复「断网后套接字半开、UI 仍显示实时连接」的检测盲区（C1 场景依赖此检测） |
| FE-G08 前置 | C1 断网/恢复用例 | `browser-smoke.mjs` 以 `routeWebSocket` 可控切断实时通道 + `setOffline` 阻断换票（CDP 离线模拟**不**切断已建立 WebSocket，需双管齐下），断言「断线重连中（已降级定时拉取）」→ 恢复 →「实时连接」 |
| FE-G08 前置 | C2 消息端到端时延实测 | 探针事件（`POST /api/v1/foundation/probes`）→ outbox 排空 → WS 推送 → 未读角标刷新计时；硬断言 ≤5s（目标 3s，受 outbox 3s 排空节奏约束），实测 2611–3101ms（两轮），数值随 PASS 行输出 |
| FE-G08 前置 | C3 axe-core 无障碍初筛 | devDependencies 增 `axe-core`；`browser-smoke.mjs` 扫描 login/workbench/ai-chat/workflow-todo/workflow-detail 五页，报告落 `artifacts/a11y-report.json`（告警不拦门禁）；**基线（2026-10-08）**：4/11/12/10/9 条规则违规（region/color-contrast/aria-required-parent 等为主），人工修复后重跑不得高于基线 |
| FE-G08 | C4 人工验收清单 | 新增 [24](24-release-acceptance-checklist.md)（浏览器矩阵 + 键盘复核 + NVDA/VoiceOver 五路径 + 时延记录 + 三方签署），[19](19-module-plan-release.md) 门槛挂接 |
| 覆盖修正 | workflow 页面渲染补缺 | 原 browser-smoke 仅经探针按钮覆盖「流程」，未渲染 `/workflow/{todo,done,detail}` 页面（§7.5 此前表述偏宽）；本轮补三页渲染断言 + 详情页 axe 扫描（详情数据由「通用申请」发起预置：LEADER 规则回落部门负责人=admin，任务落本人待办，见 `browser-smoke.mjs` seeded 段） |

**验证证据（2026-10-08）**

- 前端四门禁：`lint:eslint` 退出码 0；`build:prod` 24s；`typecheck` 退出码 0；`test` **110 例**全绿（7 文件：新增 `sanitize.test.ts` 10 例 + `upload.test.ts` 增 3 例，97 → 110）。
- `node scripts/smoke.mjs` → **PASS: 498** HTTP/WebSocket checks。
- `node scripts/browser-smoke.mjs` → PASS（含 C1 断网/恢复、C2 时延、C3 五页扫描、workflow 三页渲染；报告写入 `artifacts/a11y-report.json`）。
- 后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（agentoa-ai 80 例，见 [21](21-module-plan-ai.md) §9.2 后补强段）。

**P0 未包含清单**

- 浏览器矩阵与屏幕阅读器走查：转 [24](24-release-acceptance-checklist.md) 人工执行（发布门槛不变）；
- axe 基线的 violation 清零：本期只建基线不拦门禁，逐项修复与转门禁待人工验收后立项；
- C2 时延为单次采样：多轮统计口径待容量测试（[19](19-module-plan-release.md) 顺序 4）一并产出。
