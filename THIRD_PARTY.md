# 第三方来源与变更

AgentOA 自有代码按 MIT 许可发布（见根目录 `LICENSE`）。以下组件保留各自许可；不能据此推断整个产品可按同一许可分发。

| 组件 | 固定版本 / 来源 | 许可及处理 |
|---|---|---|
| RuoYi-Vue-Plus | `v5.6.2`，commit `8136a0191a2258c0e1b36a8146a1c5ebc070c139`，https://github.com/dromara/RuoYi-Vue-Plus | MIT；原始 LICENSE 保留于 agentoa-backend/LICENSE |
| plus-ui | `v5.6.2-v2.6.2`，commit `d0d451967676707021b9857df529c395b27e90a7`，https://github.com/JavaLionLi/plus-ui | MIT；原始 LICENSE 保留于 agentoa-frontend/LICENSE |
| Flowable | 7.2.0，Maven Central | Apache-2.0；独立集成，不启用上游 Warm-Flow |
| SeaweedFS | 4.48，commit 前缀 `530be3e37`，https://github.com/seaweedfs/seaweedfs | Apache-2.0；官方维护者 ghcr.io/chrislusf 镜像，Compose 固定 sha256 |
| MySQL Community | 8.4.6 官方镜像 | GPL-2.0；作为独立数据库服务 |
| Redis | 7.4.5 官方镜像 | RSALv2 / SSPLv1 双许可；发布、托管或商业再分发前按实际用途审查，不称作 BSD |
| Nginx / Eclipse Temurin | Compose/Dockerfile 中固定版本 | 各自上游许可与镜像内声明保留 |

原始归档 SHA256：

- 后端：`C713E2276D1EA6E8F220CD6476701A21B85EBE6B22EEE9694988EE5DEAAC5503`
- 前端：`3366E4D3E25FFDBB7F040D986F2093FAEB1E9E1327300BA782D3B52B7E0A5823`

主要改动：新增 agentoa-foundation 模块；不加载 Warm-Flow、演示和任务中心模块；使用 Redis 不透明会话；首次初始化/改密；单组织账号保护；S3 私有文件；Flowable 事务适配；outbox/站内信/一次性 WS 票据；前端登录/工作台及类型兼容修复。

未启用的前端 Warm-Flow 页面保留在 upstream-reference/frontend，避免误将其当作 Flowable 设计器。其余保留的上游示例源码不构成本项目交付功能；运行入口为根目录 README。

2026-10-02 核验中，`https://dl.min.io/` 对固定社区版本返回 HTTP 410，说明开源 MinIO/MC 已归档且不再提供维护或下载。本项目经用户选择改用仍维护的 S3 兼容存储，不依赖该归档下载站。
