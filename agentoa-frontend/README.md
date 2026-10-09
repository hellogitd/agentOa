# agentoa-frontend

AgentOA 协同办公 PC Web 前端，承载模块 1–8 业务页面与模块 10（AI）的管理与作业页面。

- 技术栈：Vue 3.5 + TypeScript 5.9 + Vite 7 + Element Plus 2.13 + Pinia + UnoCSS + vue-i18n + vxe-table
- 底座：plus-ui（RuoYi-Vue-Plus 5.6.2-2.6.2）
- 计划与约定：[docs/22-module-plan-frontend.md](../docs/22-module-plan-frontend.md)

## 端口

| 项 | 地址 | 说明 |
|---|---|---|
| 本地开发 | http://127.0.0.1:5173 | `npm run dev`，`VITE_APP_PORT` |
| 容器部署 | http://localhost:18080 | 前端静态资源容器 |
| 后端代理 | `/backend` → `http://localhost:18081` | `vite.config.ts` 代理，生产由网关转发 |

## 命令

```bash
npm ci --ignore-scripts --no-audit --no-fund   # 安装依赖（与 CI 同口径）

npm run dev            # 开发服务（5173）
npm run build:prod     # 生产构建
npm run build:dev      # 开发模式构建
npm run preview        # 预览构建产物

npm run typecheck      # 类型检查（vue-tsc --noEmit，需先 build 生成 auto-import 声明）
npm run test           # 单元测试（vitest run）
npm run test:watch     # 监听模式

npm run lint:eslint    # lint 门禁（合并门禁之一）
npm run lint:eslint:fix
npm run prettier       # 格式化
```

本地与 CI 一致的四项门禁：`lint:eslint` / `build:prod` / `typecheck` / `test`。仓库根目录
`scripts/build-frontend.ps1` 按「lint → build → typecheck → test」顺序串联；`.github/workflows/verify.yml`
同口径执行。

## 目录约定

| 目录 | 约定 |
|---|---|
| `src/api/<模块>/index.ts` | API 封装：单文件内定义 `ApiEnvelope/PageVo/VO/Form` + 具名函数（`listXxx/getXxx/addXxx/updateXxx/delXxx`） |
| `src/api/ai/stream.ts` | AI 流式调用：fetch + ReadableStream 解析 SSE（事件 `meta/step/delta/usage/done/error`，`AbortController` 停止） |
| `src/views/<业务>/<页面>/index.vue` | 页面组织，`<script setup name="Xxx" lang="ts">`；业务级复用组件放 `views/<业务>/components/` |
| `src/router/index.ts` | 仅 constantRoutes；业务路由由后端菜单 `getRouters` 下发 |
| `src/store/modules/permission.ts` | `loadView()` 把菜单 `component`（如 `ai/chat/index`）映射到 `views/**` |
| `src/directive/permission` | `v-hasPermi` / `v-hasRoles`；编程式 `$auth.hasPermi*`；权限串 `模块:子模块:动作` |
| `src/utils/request.ts` | axios 实例：Bearer token + `clientid` + `Content-Language`，envelope `{code,msg,data,timestamp,requestId}` |
| `src/utils/auth.ts` | token **仅存内存**（刷新即登出），浏览器 storage/cookie 不落 token——有意安全设计，请勿"修复" |
| `src/lang/` | vue-i18n 词条（`route/login/register/navbar` 框架文案双语；业务文案中文，见 docs/22 FE-F2-01 决策） |
| `src/**/__tests__/` | vitest 用例（纯函数/工具层） |
| `vitest.config.ts` | 测试配置（复用 `vite/plugins/auto-import` 以保持自动导入一致） |

## 验证

```powershell
# 仓库根目录
./scripts/build-frontend.ps1 -Install   # lint + build + typecheck + test
node scripts/smoke.mjs                  # 后端 HTTP/WS 契约（需本地栈）
node scripts/browser-smoke.mjs          # 无头浏览器页面渲染（需本地栈）
```
