<div align="center">

# AgentOA

**面向 10–200 人团队的开源企业协同办公（OA）平台**
**Open-source enterprise OA & collaboration platform for teams of 10–200**

[简体中文](#简体中文) · [English](#english)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-42b883.svg)](https://vuejs.org/)

一个人 · 一个国庆假期（2026）· 约 **10 亿 token**
One person · one National Day holiday (2026) · ~**1 billion tokens**

</div>

---

# 简体中文

## 关于 AgentOA

AgentOA 是一套开箱即用的企业协同办公系统，面向 10–200 人的中小团队，覆盖组织人事、流程审批、考勤打卡、报销财务、公告通知、知识库、日程协同与报表工作台八大业务域，并内置 AI 能力（多模态对话、知识库问答、业务 Copilot、Agent/MCP），附带 PC 管理端与 H5 / 微信小程序移动端。

后端基于 [RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus) 二次开发，前端基于 [plus-ui](https://github.com/JavaLionLi/plus-ui)，自研部分以 **MIT** 许可开源，上游许可证保留，详见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 这个项目是怎么来的

2026 年国庆假期，作者在家用 AI 结对编程，全程约 10 亿 token 完成了从需求分析、架构设计到编码、测试与文档的全部工作：

- 7 天交付 **8 大业务模块**，随后补齐 **AI 能力**（模型接入、对话、RAG、Copilot、Agent/MCP），功能开发完成度约 **96%**
- **378** 个业务 REST 接口 · **88** 个业务实体 · **33** 个 Flyway 迁移（V1–V33）
- **441** 个后端单元 / 集成测试、**494** 项 HTTP/WS 冒烟契约全部通过
- **96** 个 PC 页面 · **25** 个移动端页面
- 含一键启动、备份恢复演练、无头浏览器回归、k6 压测脚本与 GitHub Actions CI

如果你也在用 AI 做工程化开发，欢迎交流提 Issue。

## 功能特性

| 模块 | 能力 |
|---|---|
| **基础底座** | RBAC 权限、数据权限、审计日志、Redis 不透明会话、SeaweedFS 私有 S3 文件、事务 outbox、站内信、一次性票据 WebSocket 与断线补拉 |
| **组织人事** | 部门 / 岗位 / 员工生命周期、调岗调薪、合同管理、教育与工作经历、员工自助、薪资 AES-256-GCM 加密、导入导出 |
| **流程审批** | Flowable 7.2 固定模板审批、会签 / 或签、挂起 / 终止 / 退回 / 加签 / 催办 / 抄送、委托代理、超时提醒 |
| **考勤打卡** | 班次排班、内外勤打卡、请假 / 加班、假期额度 FIFO 批次、考勤报表 |
| **报销财务** | 报销单、发票与账单分摊、多次付款拆分、预算冻结 / 结算 / 释放、费用类型与财务报表 |
| **公告通知** | 公告、站内信、消息模板、定时推送、outbox 重投保证送达 |
| **知识库** | 空间 / 文档 / 版本 / 全文检索、评论 / 点赞 / 收藏、目录级 ACL、回收站 |
| **日程 · 会议 · 任务** | 日程、会议室预订、个人任务、重复日程（RRULE 子集）与系列 / 例外实例 |
| **报表工作台** | 18 项业务指标、HR / 考勤 / 财务看板、异步导出 |
| **AI 能力** | 多厂商模型渠道与 Key 双通道治理、SSE 多模态对话、知识库问答（RAG 引用溯源）、业务 Copilot、Agent/MCP 工具编排、用量限额 |
| **移动端** | uni-app 实现 H5 与微信小程序，25 个页面覆盖登录、工作台、审批、考勤、公告、消息、通讯录、日程、知识库、任务、报销付款、AI 助手与知识问答、我的 |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.5 · MyBatis-Plus · Sa-Token · Flowable 7.2 · LangChain4j · Flyway · Redis 7.4 · MySQL 8.4 |
| PC 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · UnoCSS |
| 移动端 | uni-app（H5 / 微信小程序） |
| 文件存储 | SeaweedFS 4.48（标准 S3 接口，私有桶） |
| 部署 | Docker Compose · Nginx · GitHub Actions |

> MinIO 社区版已停止下载维护，本项目改用仍在维护的 SeaweedFS 作为自建 S3 兼容存储，应用侧保持标准 S3 接口不变。来源与许可见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 快速开始

**环境要求**：JDK 17+、Maven、Node 20.19+（实测 24.14.0）、Docker Engine / Compose v2。Windows 下使用 Linux 容器，需设置 `JAVA_HOME`。

```powershell
./scripts/start-local.ps1 -Build
```

访问 **http://localhost:18080**（移动端 H5：**http://localhost:18082**），默认账号 `admin` / 密码 `123456`（仅限本地验证；当前凭据保存在 `deploy/secrets/admin-current-username`、`deploy/secrets/admin-current-password`）。本地栈默认关闭验证码（`deploy/.env` 中 `CAPTCHA_ENABLED=false`）便于接口调试，**生产部署必须修改默认密码并重新开启验证码**。

脚本受 PowerShell 策略限制时可改用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Build
```

**默认端口**（均仅绑定本机）：

| 服务 | 端口 |
|---|---|
| Web（Nginx） | 18080 |
| H5 移动端（Nginx） | 18082 |
| 后端 API | 18081 |
| MySQL | 13306 |
| Redis | 16379 |
| S3（SeaweedFS） | 19000 |

云端部署与安全加固见 [使用说明 · 云端服务器部署](docs/user-manual.html#cloud)。

## 目录结构

```
agentoa-backend/     后端（RuoYi-Vue-Plus 二次开发，Spring Boot 3.5）
agentoa-frontend/    PC 管理端（Vue 3 + TypeScript）
agentoa-uniapp/      移动端（uni-app：H5 / 微信小程序）
deploy/              Docker Compose、Dockerfile、Nginx、备份
scripts/             一键启动、构建、密钥初始化、备份恢复、冒烟、压测
docs/                设计与计划文档（中文）
upstream-reference/  未启用的上游参考源码
```

## 常用命令

```powershell
./scripts/build-backend.ps1            # 后端构建
./scripts/build-frontend.ps1 -Install  # 前端构建
./scripts/build-h5.ps1 -Install        # 移动端 H5 构建
./scripts/start-local.ps1              # 启动本地栈
docker compose -f deploy/compose.yml down   # 停止（不删除持久卷）
./scripts/backup-local.ps1             # 备份
./scripts/restore-local.ps1            # 恢复
node scripts/smoke.mjs                 # HTTP/WS 契约冒烟
node scripts/h5-smoke.mjs              # H5 移动端冒烟
```

## 验证与测试

- 后端 441 个单元 / 集成测试（`mvn verify`，63 个测试类全绿）
- `scripts/smoke.mjs`：494 项 HTTP/WebSocket 契约冒烟，覆盖全部模块（含 AI M1–M5）
- `scripts/browser-smoke.mjs`：无头浏览器主流程回归（断网 / 恢复、消息端到端时延、axe-core 无障碍初筛）
- `scripts/h5-smoke.mjs`：H5 移动端无头冒烟（登录 / 审批 / 打卡 / 消息 / AI）
- `agentoa-frontend`：typecheck / vitest / eslint 门禁（vitest 110 例纯函数用例）
- `scripts/loadtest/`：k6 压测场景（200 VU 混合负载）
- `.github/workflows/verify.yml`：CI 后端测试 + 端侧门禁 + 起栈 + smoke / h5-smoke
- 备份恢复演练记录见 `deploy/backups/`

## 项目状态

| 范围 | 状态 |
|---|---|
| M0 底座（认证 / 迁移 / 文件 / 消息 / 部署 / CI） | 已交付 |
| 八大业务模块 P0 | 已交付 |
| P1 增强（调薪合同、流程加签委托、排班预算、模板推送、假期 FIFO、会签或签、发票拆分、重复日程、目录 ACL） | 已交付 |
| 模块 10 AI（模型接入 / 对话 / RAG / Copilot / Agent） | 已交付（[21](docs/21-module-plan-ai.md)） |
| 移动端 H5 / 微信小程序 | 功能开发完成，真机验收未做（补强与验收计划见 [23](docs/23-module-plan-h5.md)） |
| 端侧补强计划（PC F1–F5 / H5 H1–H7，含移动 AI） | 已交付（F1/F3/F4/F5-03、H1–H6；F2 决策 B 冻结、F5-02 转 v1.1；[22](docs/22-module-plan-frontend.md) / [23](docs/23-module-plan-h5.md)），浏览器兼容矩阵与 H7 真机验收待人工（[24](docs/24-release-acceptance-checklist.md)） |
| 模块 9 发布验收（HTTPS/WSS、容量压测、RPO/RTO 演练、培训试点） | 未实施 |

**不包含**：在线可视化流程设计器（采用固定模板）、生产上线验收。生产域名、HTTPS、异机恢复与容量验收需在部署环境另行配置验证。

## 文档

> 文档目前为中文，欢迎贡献英文翻译。

| 文档 | 用途 |
|---|---|
| [09 · 底座使用与验收](docs/09-foundation.md) | 实际实现范围、运行命令、版本、接口与测试 |
| [20 · 代码完成度审查](docs/20-completion-review.md) | 逐模块代码实测与完成度统计 |
| [01 · 选型调研](docs/01-research.md) | 技术选型背景与依据 |
| [02 · 需求功能](docs/02-requirements.md) | 八大业务域与 P0/P1 边界 |
| [03 · 技术架构](docs/03-architecture.md) | 模块、事务、认证、消息设计 |
| [04 · 数据库设计](docs/04-database-design.md) | 业务数据模型（实际迁移在代码中） |
| [05 · API 规范](docs/05-api-spec.md) | 业务接口规范 |
| [06 · 部署运维](docs/06-deployment.md) | 生产发布与恢复目标 |
| [07 · 路线图](docs/07-roadmap.md) | M0–M4 依赖与人力估算 |
| [10 · 模块计划总览](docs/10-module-plan-index.md) | 模块开发顺序与完成定义 |
| [11](docs/11-module-plan-hr.md) · [12](docs/12-module-plan-workflow.md) · [13](docs/13-module-plan-attendance.md) · [14](docs/14-module-plan-finance.md) | 人事 / 流程 / 考勤 / 财务模块计划 |
| [15](docs/15-module-plan-notice.md) · [16](docs/16-module-plan-knowledge.md) · [17](docs/17-module-plan-collaboration.md) · [18](docs/18-module-plan-reporting.md) | 通知 / 知识 / 协同 / 报表模块计划 |
| [19 · 发布验收计划](docs/19-module-plan-release.md) | H5、性能、恢复与 v1.0 发布门槛 |
| [21 · AI 能力计划](docs/21-module-plan-ai.md) | 模型接入层、对话、RAG 问答、Copilot、Agent/MCP 分期 |
| [22 · PC 前端计划](docs/22-module-plan-frontend.md) · [23 · H5/移动端计划](docs/23-module-plan-h5.md) | 端侧现状盘点、差距登记与补强分期（F1–F5 / H1–H7） |
| [24 · 发布验收清单](docs/24-release-acceptance-checklist.md) | 人工验收执行与签署（浏览器矩阵、无障碍、真机验收） |
| [使用说明](docs/user-manual.html) | 用户手册（含云端部署章节） |

## 贡献

欢迎 Issue 与 Pull Request。提交代码前请同步更新 [09](docs/09-foundation.md) 中的实现状态与验证范围。

## 许可证

自研代码以 [MIT](LICENSE) 许可开源。上游及第三方组件保留各自许可证，详见 [THIRD_PARTY.md](THIRD_PARTY.md)。

> **注意**：Redis（RSALv2/SSPLv1）与 MySQL（GPL-2.0）等组件以独立服务运行，本项目许可证不覆盖其再分发条款；商业托管或再分发前请按实际用途自行审查。

---

# English

## About AgentOA

AgentOA is an out-of-the-box enterprise OA (office automation) and collaboration platform for small-to-mid teams of 10–200 people. It covers eight business domains — HR & org, workflow approvals, attendance, expense & finance, announcements, knowledge base, calendar/meetings/tasks, and reporting — with built-in AI capabilities (multimodal chat, knowledge Q&A, business copilot, agent/MCP), plus a PC admin console and an H5 / WeChat mini-program mobile client.

The backend is built on [RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus) and the frontend on [plus-ui](https://github.com/JavaLionLi/plus-ui). Original code is open-sourced under the **MIT** license; upstream licenses are preserved — see [THIRD_PARTY.md](THIRD_PARTY.md).

## The story

During the 2026 National Day holiday, this project was built at home by one person working with AI pair programming — about **1 billion tokens** across requirements, architecture, code, tests and docs:

- **8 business modules** delivered in 7 days, then completed **AI capabilities** (model access, chat, RAG, copilot, agent/MCP) — ~**96%** feature completion
- **378** business REST endpoints · **88** business entities · **33** Flyway migrations (V1–V33)
- **441** backend unit/integration tests and **494** HTTP/WS smoke contract checks, all passing
- **96** PC pages · **25** mobile pages
- One-command local stack, backup/restore drills, headless-browser regression, k6 load tests, GitHub Actions CI

If you are also building software with AI, issues and discussions are welcome.

## Features

| Module | Capabilities |
|---|---|
| **Foundation** | RBAC & data permissions, audit logs, opaque Redis sessions, private S3 files (SeaweedFS), transactional outbox, in-app mail, one-time-ticket WebSocket with reconnect catch-up |
| **HR & Org** | Departments, posts, employee lifecycle, transfers & salary changes, contracts, education/work history, self-service portal, AES-256-GCM salary encryption, import/export |
| **Workflow** | Flowable 7.2 fixed-template approvals, countersign / or-sign, suspend / terminate / return / add-sign / urge / CC, delegation, timeout reminders |
| **Attendance** | Shifts & scheduling, on-site & field punch, leave / overtime, leave balance FIFO batches, attendance reports |
| **Expense & Finance** | Expense claims, invoice & bill allocation, split payments, budget freeze / settle / release, expense types, finance reports |
| **Announcements** | Announcements, in-app messaging, message templates, scheduled push, outbox-based delivery guarantees |
| **Knowledge base** | Spaces, documents, versions, full-text search, comments / likes / favorites, directory-level ACL, trash |
| **Calendar · Meeting · Tasks** | Events, meeting-room booking, personal tasks, recurring events (RRULE subset) with series/exception instances |
| **Reporting** | 18 business metrics, HR / attendance / finance dashboards, async export |
| **AI capabilities** | Multi-provider model channels with dual-channel key management, SSE multimodal chat, knowledge Q&A (RAG with citations), business copilot, agent/MCP tool orchestration, usage quotas |
| **Mobile** | uni-app H5 & WeChat mini-program, 25 pages covering login, workbench, approvals, attendance, announcements, messaging, contacts, calendar, knowledge, tasks, reimbursements & payments, AI assistant & knowledge Q&A, profile |

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 3.5 · MyBatis-Plus · Sa-Token · Flowable 7.2 · LangChain4j · Flyway · Redis 7.4 · MySQL 8.4 |
| PC frontend | Vue 3 · TypeScript · Vite · Element Plus · Pinia · UnoCSS |
| Mobile | uni-app (H5 / WeChat mini-program) |
| File storage | SeaweedFS 4.48 (standard S3 API, private buckets) |
| Deployment | Docker Compose · Nginx · GitHub Actions |

> MinIO's community edition downloads have been discontinued, so this project uses the still-maintained SeaweedFS as self-hosted S3-compatible storage while keeping the standard S3 API on the application side. See [THIRD_PARTY.md](THIRD_PARTY.md) for sources and licenses.

## Quick start

**Requirements**: JDK 17+, Maven, Node 20.19+ (verified with 24.14.0), Docker Engine / Compose v2. On Windows use Linux containers and set `JAVA_HOME`.

```powershell
./scripts/start-local.ps1 -Build
```

Open **http://localhost:18080** (mobile H5: **http://localhost:18082**) — default account `admin` / password `123456` (local verification only; current credentials are stored in `deploy/secrets/admin-current-username` and `deploy/secrets/admin-current-password`). The local stack disables captcha by default (`CAPTCHA_ENABLED=false` in `deploy/.env`) for easier API debugging. **For production you must change the default password and re-enable captcha.**

If script execution is blocked by PowerShell policy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-local.ps1 -Build
```

**Default ports** (all bound to localhost only):

| Service | Port |
|---|---|
| Web (Nginx) | 18080 |
| Mobile H5 (Nginx) | 18082 |
| Backend API | 18081 |
| MySQL | 13306 |
| Redis | 16379 |
| S3 (SeaweedFS) | 19000 |

Cloud deployment and security hardening: see [User Manual · Cloud deployment](docs/user-manual.html#cloud).

## Repository layout

```
agentoa-backend/     Backend (RuoYi-Vue-Plus based, Spring Boot 3.5)
agentoa-frontend/    PC admin console (Vue 3 + TypeScript)
agentoa-uniapp/      Mobile client (uni-app: H5 / WeChat mini-program)
deploy/              Docker Compose, Dockerfiles, Nginx, backups
scripts/             Start, build, secrets init, backup/restore, smoke, load test
docs/                Design & planning documents (Chinese)
upstream-reference/  Unused upstream reference sources
```

## Common commands

```powershell
./scripts/build-backend.ps1            # Build backend
./scripts/build-frontend.ps1 -Install  # Build frontend
./scripts/build-h5.ps1 -Install        # Build mobile H5
./scripts/start-local.ps1              # Start local stack
docker compose -f deploy/compose.yml down   # Stop (keeps persistent volumes)
./scripts/backup-local.ps1             # Backup
./scripts/restore-local.ps1            # Restore
node scripts/smoke.mjs                 # HTTP/WS contract smoke tests
node scripts/h5-smoke.mjs              # H5 mobile smoke
```

## Verification & testing

- 441 backend unit/integration tests (`mvn verify`, 63 test classes green)
- `scripts/smoke.mjs`: 494 HTTP/WebSocket contract smoke checks across all modules (incl. AI M1–M5)
- `scripts/browser-smoke.mjs`: headless-browser main-flow regression (offline/recovery, message latency, axe-core a11y pre-scan)
- `scripts/h5-smoke.mjs`: mobile H5 headless smoke (login / approvals / punch / messages / AI)
- `agentoa-frontend`: typecheck / vitest / eslint gates (110 pure-function vitest cases)
- `scripts/loadtest/`: k6 load scenarios (200 VU mixed workload)
- `.github/workflows/verify.yml`: CI backend tests + client gates + stack startup + smoke / h5-smoke
- Backup/restore drill artifacts under `deploy/backups/`

## Project status

| Scope | Status |
|---|---|
| M0 foundation (auth / migrations / files / messaging / deploy / CI) | Delivered |
| Eight business modules (P0) | Delivered |
| P1 enhancements (salary & contracts, add-sign & delegation, scheduling & budgets, templates & scheduled push, leave FIFO, countersign/or-sign, split payments, recurring events, directory ACL) | Delivered |
| Module 10 AI (model access / chat / RAG / copilot / agent) | Delivered ([21](docs/21-module-plan-ai.md)) |
| Mobile H5 / WeChat mini-program | Feature-complete, real-device acceptance pending (see [23](docs/23-module-plan-h5.md)) |
| Client-side hardening plans (PC F1–F5 / H5 H1–H7, incl. mobile AI) | Delivered (F1/F3/F4/F5-03, H1–H6; F2 frozen as decision B, F5-02 moved to v1.1; [22](docs/22-module-plan-frontend.md) / [23](docs/23-module-plan-h5.md)); browser matrix & H7 real-device acceptance pending manual ([24](docs/24-release-acceptance-checklist.md)) |
| Module 9 release acceptance (HTTPS/WSS, load testing, RPO/RTO drills, training & pilot) | Not started |

**Not included**: online visual workflow designer (fixed templates are used instead), production go-live acceptance. Production domains, HTTPS, cross-host recovery and capacity verification must be configured and validated in your target environment.

## Documentation

> Documentation is currently in Chinese; English translations are welcome.

| Document | Purpose |
|---|---|
| [09 · Foundation usage & acceptance](docs/09-foundation.md) | Actual scope, commands, versions, APIs and tests |
| [20 · Code completion review](docs/20-completion-review.md) | Per-module measured completion statistics |
| [01 · Technology research](docs/01-research.md) | Selection background and rationale |
| [02 · Requirements](docs/02-requirements.md) | Eight business domains and P0/P1 boundaries |
| [03 · Architecture](docs/03-architecture.md) | Modules, transactions, auth, messaging design |
| [04 · Database design](docs/04-database-design.md) | Data model (actual migrations live in code) |
| [05 · API spec](docs/05-api-spec.md) | Business API conventions |
| [06 · Deployment & ops](docs/06-deployment.md) | Production release and recovery targets |
| [07 · Roadmap](docs/07-roadmap.md) | M0–M4 dependencies and effort estimates |
| [10 · Module plan index](docs/10-module-plan-index.md) | Module order and definition of done |
| [11](docs/11-module-plan-hr.md) · [12](docs/12-module-plan-workflow.md) · [13](docs/13-module-plan-attendance.md) · [14](docs/14-module-plan-finance.md) | HR / workflow / attendance / finance plans |
| [15](docs/15-module-plan-notice.md) · [16](docs/16-module-plan-knowledge.md) · [17](docs/17-module-plan-collaboration.md) · [18](docs/18-module-plan-reporting.md) | Notice / knowledge / collaboration / reporting plans |
| [19 · Release acceptance plan](docs/19-module-plan-release.md) | H5, performance, recovery and v1.0 gates |
| [21 · AI capability plan](docs/21-module-plan-ai.md) | Model access, chat, RAG Q&A, copilot, agent/MCP phases |
| [22 · PC frontend plan](docs/22-module-plan-frontend.md) · [23 · H5/mobile plan](docs/23-module-plan-h5.md) | Client-side baseline, gap register and hardening phases (F1–F5 / H1–H7) |
| [24 · Release acceptance checklist](docs/24-release-acceptance-checklist.md) | Manual acceptance execution & sign-off (browser matrix, a11y, real-device) |
| [User manual](docs/user-manual.html) | End-user manual (incl. cloud deployment) |

## Contributing

Issues and pull requests are welcome. Please keep [09](docs/09-foundation.md) in sync with the actual implementation and verification status.

## License

Original code is released under the [MIT](LICENSE) license. Upstream and third-party components keep their own licenses — see [THIRD_PARTY.md](THIRD_PARTY.md).

> **Note**: components such as Redis (RSALv2/SSPLv1) and MySQL (GPL-2.0) run as independent services; this project's license does not cover their redistribution terms. Review them before commercial hosting or redistribution.
