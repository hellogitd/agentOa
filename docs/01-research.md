# 01 · 选型调研与验证计划

> 版本：v1.1（审查修订草案）
> 更新日期：2026-10-02
> 实施状态（2026-10-02）：M0 基础工程已落地，实际版本、命令、接口与验证见 [09 · 底座使用与验收](09-foundation.md)。本文尚未标注实现的业务能力仍为设计。
> 状态：选型记录；M0 已锁定并验证 RuoYi-Vue-Plus、Flowable、SeaweedFS 等版本，业务模块选型仍按实施需要复核

## 1. 结论与证据边界

建议继续评估 **RuoYi-Vue-Plus + Flowable + 自研 OA 模块**。模块化单体适合本项目规模，但底座与工作流的组合必须先做最小验证。

原稿将 `ruoyi-vue-pro` 与 `RuoYi-Vue-Plus` 当成同一个项目，并据此宣称已经内置 Flowable、CRM/ERP 和表单设计器，不能作为实施依据。本轮只核对本地文档，没有在线核实上游最新发布、维护状态、星标数或许可证；删除无证据的数字、排名和“唯一选择”等结论。

## 2. 候选项目与边界

下列地址是后续查证入口，不能替代固定版本的源码和 LICENSE 检查。

| 候选 | 官方仓库入口 | 本项目关注点 | 必须验证 |
|---|---|---|---|
| RuoYi-Vue-Plus | https://github.com/dromara/RuoYi-Vue-Plus | Java 后台底座、权限、模块组织 | 目标分支的 JDK/Boot、认证、调度器、工作流组件及配套前端 |
| ruoyi-vue-pro（芋道） | https://github.com/YunaiV/ruoyi-vue-pro | 含更多业务模块的另一套候选底座 | 与 Plus 的技术和许可差异、模块依赖、裁剪成本 |
| RuoYi-Flowable-Plus | https://github.com/KonBAI-Q/RuoYi-Flowable-Plus | 工作流集成参考 | 维护状态、引擎版本、许可和可迁移代码范围 |
| JeecgBoot | https://github.com/jeecgboot/JeecgBoot | 低代码候选底座 | 开源/商业功能边界、流程和表单的实际可用性 |
| SmartAdmin | https://github.com/1024-lab/smart-admin | 后台框架备选 | 权限和工程能力、另行开发 OA 的成本 |
| O2OA | https://github.com/o2oa/o2oa | 成品 OA 路线备选 | 业务匹配、二开成本、目标版本许可义务 |

选择底座时优先比较实际通过的业务样例、团队熟悉度、升级路径和二开工作量。星标数不代表功能已集成，也不证明安全或合规。

## 3. 工作流与辅助组件

| 用途 | 建议 | 选型理由 | 验证项 |
|---|---|---|---|
| 工作流 | [Flowable](https://github.com/flowable/flowable-engine) | BPMN 建模与 Java 嵌入式集成符合目标 | Boot/JDK 兼容、共享事务、任务并发、升级迁移 |
| 备选引擎 | [Activiti](https://github.com/Activiti/Activiti)、[Camunda](https://github.com/camunda/camunda) | 在 Flowable 验证失败时比较 | 分别核对版本、支持周期和许可；不混用 Camunda 7/8 的结论 |
| 对象存储 | SeaweedFS S3 接口或其他仍维护的 S3 兼容服务 | 文件与数据库分离 | 发行方式、镜像来源、许可、备份恢复、应用账号权限 |
| 文档搜索 | MySQL FULLTEXT + ngram 起步 | 减少基础设施 | 中文分词、搜索权限过滤、实测数据量与延迟 |
| 消息 | MySQL outbox + 站内信 + WebSocket | 保证提交后可重试送达 | 幂等消费、断线补拉、多实例推送 |
| 移动 | [uni-app](https://uniapp.dcloud.net.cn) 候选，H5 优先 | 覆盖基础移动场景 | Vue 3 组件兼容、定位授权、相机、各宿主平台限制 |
| Office 预览 | [ONLYOFFICE](https://github.com/ONLYOFFICE/DocumentServer) 等后续评估 | 非首版核心闭环 | 版本许可、资源消耗、鉴权与文件外传边界 |

IM、项目管理成品和在线文档服务可作为采购或集成备选；首版不同时部署多个协同平台。Elasticsearch、MQ、Kubernetes 需有实测容量或运维需求再引入。

## 4. M0 最小验证与决策记录

| 验证 | 通过条件 | 证据 |
|---|---|---|
| 底座启动 | 干净环境完成构建、登录、角色和部门权限验证 | 仓库 URL、tag/commit、构建日志 |
| 工作流闭环 | 请假发起 → 审批 → 业务状态更新；失败时整体回滚 | BPMN 样例、测试及事务配置 |
| 并发与重试 | 同一任务两人提交仅一次成功；请求重放不重复扣额度 | 并发测试记录 |
| 表单版本 | 发布新模板不改变历史实例；服务器拒绝隐藏字段篡改 | 表单版本快照、校验测试 |
| 前端与 H5 | 配套前端可构建；H5 定位拒绝时有明确交互 | 锁文件、构建与真机记录 |
| 存储 | 私有文件上传、授权下载、恢复后可读取 | 权限与恢复测试记录 |
| 依赖与许可 | 确认使用范围、版本、许可证和声明保留位置 | 组件清单和许可证快照 |

决策记录至少包含：候选、决策日期、负责人、固定版本、通过/失败项、剩余风险、替代方案。未通过时调整架构和估算，再进入 M1；不得把预研假设当作已验证事实。

## 5. 许可证核对原则

| 类型 | 需要核对的事项 |
|---|---|
| MIT / Apache-2.0 | 对应版本及依赖的版权、许可证、NOTICE 等保留要求 |
| GPL / AGPL | 根据修改、组合、分发和网络交互方式判断源代码提供义务；不能概括成禁止商用 |
| MPL 等 | 修改文件和分发范围的具体义务 |
| BSL / PolyForm / 商业或混合许可 | 阅读该版本的具体条款、附加授权和商业边界 |
| 未声明 / NOASSERTION | 表示尚未确认，不能视为自由使用授权 |

MySQL、Redis、SeaweedFS 及其镜像的许可应按具体产品、版本和发行方式核实，不能统称为 MIT/Apache 生态。AgentOA 当前没有 `LICENSE`，拟采用的自研代码许可需单独确定。

## 6. 待关闭事项

- 底座后端与配套前端的仓库、commit、版本兼容矩阵。
- 工作流集成、表单格式、任务调度和 WebSocket 的适配成本。
- 数据库、Redis、对象存储镜像及许可证清单。
- 团队实际投入、企业考勤规则、试点端和首版验收范围。

相关文档：[需求](02-requirements.md) · [架构](03-architecture.md) · [路线图](07-roadmap.md)
