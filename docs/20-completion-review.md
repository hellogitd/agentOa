# 20 · 实际代码完成度审查报告

> 审查日期：2026-10-05
> 审查方式：以 [10 · 模块计划总览](10-module-plan-index.md)、[11–19 模块计划](10-module-plan-index.md) 与 [09 · 底座使用与验收](09-foundation.md) 交付记录为基线，逐模块核对 `agentoa-backend`、`agentoa-frontend`、`agentoa-uniapp`、`scripts`、`deploy` 的实际代码，统计迁移、接口、测试、页面与验证脚本证据。所有数字均来自本仓库实测（统计口径见 §2），不引用未验证的估算。

## 1. 总体结论

| 范围 | 状态 | 说明 |
|---|---|---|
| M0 底座（认证/迁移/文件/消息/部署/CI） | ✅ 已交付 | 迁移 V1–V3，`scripts/*` 一键启动、备份恢复、冒烟脚本齐全 |
| 模块 1–8 P0（组织人事 / 流程 / 考勤 / 财务 / 通知 / 知识 / 协同 / 报表） | ✅ 已交付 | 八模块后端+PC 前端+权限+测试全部落地，与 docs/05 接口契约对齐 |
| P1 批次一（V12–V16：调岗调薪/合同、流程挂起退回加签催办委托、外勤排班、预算、知识社交） | ✅ 已交付 | docs/09 §15 有交付记录，代码与测试一致 |
| P1 批次二（V17–V22：模板定时推送 C、假期批次 E、会签/或签 A、预算与发票拆分 B、重复日程 D、目录 ACL F） | ✅ 代码已落地 / ⚠️ 文档记录不全 | 六个迁移 V17–V22 与实现、测试、前端页面均在；docs/09 §16 仅完整登记 C、E 两项，A/B/D/F 交付记录待补写 |
| 模块 9 发布与验收（docs/19：HTTPS/WSS、容量压测、恢复演练 RPO/RTO、H5 真机、培训、试点签字） | ❌ 未实施 | 唯一一整块未完成的计划内容，含用户手册（本报告配套的 `user-manual.html` 覆盖用户手册部分） |
| 移动端 H5 / 小程序 | ✅ 功能开发完成 / ⚠️ 真机验收未做 | 25 页面（H1–H6 已交付：质量门禁、定位/外勤、表单附件与报销闭环、移动协同、实时消息、移动 AI，见 [23](23-module-plan-h5.md) §7），H5 与微信小程序构建链路已跑通（`agentoa-uniapp/dist/build` 有产物） |

**功能开发完成度约 96%；发布验收（模块 9）完成度约 20%（仅本地构建/冒烟/备份恢复演练）。**
P0 范围（v1.0 必须能力）在功能层面已全部落地，距 v1.0 发布只差 docs/19 的验收门槛项。

## 2. 审查口径与代码规模实测

统计口径：`agentoa-backend/ruoyi-modules/agentoa-*` 下 `src/main`、`src/test`（排除 `target/`）；前端/移动端排除 `node_modules`、`dist`。端点按 `@Get/@Post/@Put/@Delete/@PatchMapping` 注解计数。

### 2.1 后端（agentoa-backend，9 个业务模块）

| 指标 | 实测值 |
|---|---|
| Java 文件 | 613（main 562 / test 51） |
| `@RestController` 控制器 | 54 |
| REST 端点（Mapping 注解） | 288 |
| Service 接口/实现对 | 44（另有 support 支撑类 40+） |
| 实体类（`@TableName`） | 72（另有 4 张关联/偏好表经原生 Mapper 访问） |
| Flyway 迁移 | 22 个（V1–V22 连续无缺口） |
| 测试 | 42 个测试类 + 9 个 `*TestEnvironment` 支撑类，**283 个 `@Test`** |
| 权限注解 | 157 处 `@SaCheckPermission` + 8 个 `*AccessPolicy` 运行时鉴权类 |
| 业务代码 TODO/FIXME/未实现 | **0 处**（仅上游 RuoYi demo 遗留 10 处框架 TODO） |

### 2.2 前端（agentoa-frontend，PC 管理端）

| 指标 | 实测值 |
|---|---|
| 页面 `views/**/*.vue` | 90（OA 业务 48 + 根级 3 + 系统/监控/工具/示例/错误页 39） |
| API 封装 | 68 个 `.ts`，约 480 个导出接口函数 |
| 全局组件 | 21 个（另有 4 个模块内局部组件） |
| 构建脚本 | `dev`、`build:prod`、`build:dev`、`preview`、`lint:eslint[:fix]`、`prettier` |
| 单元测试 | **0**（vitest 已装但无用例/配置） |
| TODO/FIXME | 0 |

### 2.3 移动端（agentoa-uniapp，H5 / 微信小程序）

| 指标 | 实测值 |
|---|---|
| 页面 | 14（4 个 tabBar + 登录/审批/考勤/公告/消息/我的等） |
| API 封装 | 6 个 `.js`，约 55 个导出函数 |
| 公共组件 | 7 |
| 构建脚本 | `dev:h5`、`dev:mp-weixin`、`build:h5`、`build:mp-weixin` |
| 已验证端 | H5、微信小程序（`dist/build/{h5,mp-weixin}` 产物存在）；APP/支付宝仅 manifest 预留 |
| `typecheck` 脚本 | ✅ 已接入真实校验（[23](23-module-plan-h5.md) H1，2026-10-08）：`vue-tsc --noEmit`（`checkJs` 非严格）+ `lint:eslint` 双门禁入 `build-h5.ps1`/`verify.yml` |

### 2.4 工程与验证资产

- `scripts/start-local.ps1`（一键起栈）、`build-backend.ps1`、`build-frontend.ps1`、`init-secrets.ps1`、`backup-local.ps1`、`restore-local.ps1`；
- `scripts/smoke.mjs`（HTTP/WebSocket 契约冒烟，58 KB，按模块组织，docs/09 记录累计 318 项全通过）、`scripts/browser-smoke.mjs`（无头浏览器主流程回归）、`scripts/loadtest/`（压测脚本，含 README）；
- `deploy/compose.yml`（mysql/redis/storage/migrate/backend/web 六服务 + secret 注入）、`compose.smoke.yml`、`nginx.conf`、`.github/workflows/verify.yml`；
- 备份恢复演练记录：`deploy/backups/20261002-125621/`（SQL + 存储归档 + SHA256 校验，恢复验证通过）。

## 3. 模块完成度对照表

状态图例：✅ 完成（含测试与前端）｜⚠️ 完成但有记录/质量缺口｜❌ 未做

| # | 模块（计划文档） | 迁移 | 端点 | 实体 | `@Test` | PC 页面 | P0 | P1 | 备注 |
|---:|---|---|---:|---:|---:|---:|:--:|:--:|---|
| 0 | M0 底座（docs/09） | V1–V3 | 11 | —（5 表） | 1 | 工作台/消息/私有文件 | ✅ | — | 底座含消息、WS 票据、outbox、探针 |
| 1 | 组织人事（docs/11） | V4、V12 | 45 | 10 | 48 | 5（employee/dept/post/contract/change） | ✅ | ✅ 批次一 | 含薪资 AES-256-GCM、导入导出、自助档案 |
| 2 | 流程审批（docs/12） | V5、V13、V19 | 63 | 15 | 38 | 9（todo/done/mine/cc/definition/monitor/delegate/detail/FormSchema） | ✅ | ✅ 批次一+二 | 会签/或签固定模板（V19）、挂起/终止/退回/加签/催办/抄送/委托/超时扫描 |
| 3 | 考勤打卡（docs/13） | V6、V14、V18 | 37 | 15 | 53 | 7（punch/balance/group/shift/calendar/report/schedule） | ✅ | ✅ 批次一+二 | 外勤打卡、排班、假期批次 FIFO（V18） |
| 4 | 报销财务（docs/14） | V7、V15、V20 | 21 | 9 | 28 | 6（reimburse/invoice/expense-type/payment/report/budget） | ✅ | ✅ 批次一+二 | 预算、发票/账单分摊与多次付款拆分（V20） |
| 5 | 公告通知（docs/15） | V8、V17 | 28 | 5 | 35 | 4（announcement/message/template/schedule） | ✅ | ✅ 批次二 C | 模板 + 定时推送 + outbox 重投 |
| 6 | 知识库（docs/16） | V9、V16、V22 | 39 | 10 | 32 | 6（space/document/files/search/favorite/trash） | ✅ | ✅ 批次一+二 | 评论/点赞/收藏（V16）、目录 ACL（V22） |
| 7 | 日程会议任务（docs/17） | V10、V21 | 32 | 5 | 25 | 3（calendar/event、calendar/room、task/mine） | ✅ | ✅ 批次二 D | 重复日程 RRULE 子集 + 系列/例外实例（V21） |
| 8 | 报表工作台（docs/18） | V11 | 11 | 3 | 23 | 8（report/{hr,attendance,finance,exports} + 3 组件） | ✅ | — | 18 项指标口径、异步导出 |
| 9 | 发布与验收（docs/19） | — | — | — | — | — | ❌ | ❌ | 未实施，见 §6 |
| — | 移动端（docs/19 范围） | — | — | — | — | 25 页（uniapp） | ✅ | — | H5/小程序已构建；H1–H6 已交付（[23](23-module-plan-h5.md) §7）；真机验收未做 |

模块合计：288 端点 / 72 实体 / 283 个 `@Test` / 52 个 OA 业务页面（PC 48 + 移动 14 分别统计）。

## 4. 验证证据核对

docs/09 各模块"验证证据"声明与代码一致性抽查：

| 声明 | 核对结果 |
|---|---|
| 空库迁移与存量升级各一次（V1→Vn / 单步） | ✅ 迁移文件齐备且版本连续；docs/09 逐模块记录了执行结果 |
| `agentoa-*` 模块 H2 集成测试 | ✅ 42 个测试类 283 个 `@Test`（实测），含每模块 `*AccessPolicyTest` 权限矩阵 |
| `smoke.mjs` 模块契约链（幂等 409、越权 403/404、状态机 409、并发） | ✅ 脚本按模块组织（58 KB），docs/09 记录 318 项全通过 |
| `browser-smoke.mjs` 页面渲染回归 | ✅ 脚本存在（Playwright + 本机 Edge） |
| 前端 `build:prod` + `vue-tsc` | ✅ `dist/` 有产物；已由端侧 F1 封装为 `npm run typecheck` 并纳入 CI 门禁 |
| 备份恢复（RPO/RTO） | ⚠️ 仅做一致性备份+恢复正确性验证，**未测 RPO≤24h/RTO≤4h 目标**（docs/09 §7 亦如实声明） |
| 负载/容量基线 | ⚠️ `scripts/loadtest/` 已备脚本，未产出基线报告 |
| H5 真机验证 | ❌ 未做（docs/19 门槛项） |

## 5. 文档与代码差异登记（需修订项）

| 差异 | 位置 | 建议 |
|---|---|---|
| docs/09 §16 标题仍为"P1 批次二（进行中）"，仅登记 C、E 两项；A（V19 会签/或签）、B（V20 预算/发票拆分）、D（V21 重复日程）、F（V22 目录 ACL）代码、测试、页面均已落地 | `docs/09-foundation.md` §16 | 补写 A/B/D/F 交付记录并更新标题状态 |
| 代码使用 `nt:schedule:list`、`nt:template:list` 权限串，V8/V17 的 `sys_menu` 未登记这两个 perms | `agentoa-notice` vs `V8/V17__notice*.sql` | 补登记菜单权限串或改用已登记串 |
| docs/04 草案表名（`at_*`/`fn_*`/`kb_*`/`cl_*`）与实际 `oa_*` 前缀、目录并入 `parent_id` 等差异 | `docs/04-database-design.md` | 已在 docs/04/09 注明"以实现口径为准"，保持同步即可 |
| ~~前端 vitest 已安装但零用例；`vue-tsc` 无 npm 脚本~~ **已闭环（端侧 F1，2026-10-07）** | `agentoa-frontend/package.json` | `typecheck` 脚本与 77 例最小用例已落地，`verify.yml` 纳入 lint/build/typecheck/test 四门禁 |
| ~~uniapp `typecheck` 为空占位脚本~~ **已闭环（[23](23-module-plan-h5.md) H1，2026-10-08）** | `agentoa-uniapp/package.json` | `typecheck`=`vue-tsc --noEmit` + `lint:eslint` 真实生效并入 CI 门禁 |
| 文档标题日期与迁移批次标注不一致（如 docs/09 §16 标 2026-10-05 进行中） | docs/09、docs/10 | 随 §16 记录补写一并更新 |

## 6. 未完成项清单（按优先级）

### 6.1 模块 9 发布与验收（docs/19，v1.0 门槛，全部未做）

1. 冻结 API/迁移/镜像/配置版本，生成发布清单与回滚点；
2. 全量回归（认证、HR、流程、考勤、报销、公告、知识、协同、报表）；
3. 安全回归（越权、敏感字段、文件下载、日志脱敏、会话失效、请求追踪）；
4. 容量测试（登录、列表、打卡、待办、上传、报表、WebSocket），产出基线报告；
5. 隔离环境数据库/文件恢复演练，实测 RPO≤24h / RTO≤4h；迁移失败、后端回滚、消息重投演练；
6. H5 真机测试、浏览器兼容、无障碍关键路径、**用户手册与管理员运维手册**（`docs/user-manual.html` 已覆盖用户手册与云端服务器部署教程，可作为运维手册主体）；
7. 试点发布与阻断/严重缺陷清零，业务/运维/回滚三方签字。

### 6.2 生产化运维缺口（docs/09 §7 声明）

- HTTPS/WSS、外部密钥管理、日志轮转、告警、异机备份未配置（当前 Compose 仅绑定本机地址）；
- outbox 失败 5 次转 FAILED 后仅管理员接口重投，无运维面板；
- 监控指标/告警对接未做。

### 6.3 P1 剩余边界（按各模块计划 P1 清单，功能裁剪版）

- 会签/或签仅固定真实模板（无设计器）、BPMN 上传导入未实现；
- 催办/超时统计、新版报表口径扩展；邮件/短信/企业微信等外部通知渠道；
- OCR 发票识别、在线/真实银行支付、Office 协同编辑（P1/P2）、ES 高级检索（P2）；
- 重复日程为 RRULE 子集（DAILY/WEEKLY/MONTHLY/YEARLY + INTERVAL + BYDAY/BYMONTHDAY + COUNT/UNTIL）；
- 移动端 APP/支付宝小程序仅 manifest 预留。

### 6.4 工程质量小缺口

- ~~前端 0 单测、类型检查未纳入脚本/CI~~ **已由端侧 F1 闭环（2026-10-07）**：`typecheck`/`test`/`lint:eslint` 三个 npm 脚本落地并纳入 `verify.yml` 与 `scripts/build-frontend.ps1`（vitest 77 例、lint 存量 194 项清零、`V33` 移除悬空菜单 132）；uniapp `typecheck` 空脚本仍待治理（[23](23-module-plan-h5.md) 范围）；
- 权限串登记差异（§5）。

> **端侧决策附注（2026-10-07，对应 docs/22 §6.1）**：① i18n 策略定为 **B（业务仅中文、框架文案双语）**，业务文案不接入 vue-i18n，`src/lang` 保持现有 4 组 key；② 通知通道 **保留按部署配置**，`VITE_APP_SSE`/`VITE_APP_WEBSOCKET` 默认 `false`；③ vitest **仅纯函数/工具层**，不引入 `@vue/test-utils`。三条均已回写 docs/22 §6.1 并冻结。

> **端侧 F3/F4 与 AI 复核附注（2026-10-08）**：① 通知实时化（docs/22 F3）已交付——站内 ticket WS 全局接入、未读角标推送+拉取双轨去重（`layout/components/notice` 铃铛面板）、指数退避重连与断线降级拉取（`utils/realtime`）；② 大文件拆分与 N+1（FE-G07）已闭环（`hr/employee`、`system/dict`、`ai/kb` 拆组件；知识批量详情接口见 [16](16-module-plan-knowledge.md) 变更记录），echarts 按需引入并记录 dist 基线；浏览器矩阵与无障碍验收（FE-G08）仍待人工执行（[19](19-module-plan-release.md) 门槛）；③ AI 模块（docs/21）复核补强：限额流式中途熔断接线、4 项契约修复补回归用例、月周期限额与 Agent 熔断用例补齐、MCP 归一化范围扩大，H2 用例 69→77（详见 docs/21「代码复核补强」）。

## 7. 结论与建议

1. **P0 八模块 + 底座 + 移动端功能面已达 v1.0 范围**，代码无遗留 TODO，测试（283）与契约冒烟覆盖幂等/并发/越权等关键路径，可进入发布验收阶段。
2. **v1.0 发布的关键路径是模块 9 验收门槛**：建议按 docs/19 顺序先做全量回归与安全回归，再做容量基线与恢复演练（含 RPO/RTO 实测），最后试点签字。
3. **先补文档账**：docs/09 §16 补写 A/B/D/F 交付记录、修正 §5 差异项，使"文档完成度"与"代码完成度"一致。
4. **质量补强**：前端 `typecheck` 脚本、最小用例与 `vue-tsc`/lint 纳入 `verify.yml` **已由端侧 F1 完成（2026-10-07）**；uniapp 空 `typecheck` **已由 [23](23-module-plan-h5.md) H1 闭环（2026-10-08）**（`vue-tsc --noEmit` + `lint:eslint` + `h5-smoke.mjs` 入 CI）。

相关文档：[09 · 底座使用与验收](09-foundation.md) · [10 · 模块计划总览](10-module-plan-index.md) · [19 · 发布计划](19-module-plan-release.md) · [用户使用说明（HTML）](user-manual.html)
