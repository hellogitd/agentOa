# 模块 10：AI 能力（M1–M5）需求与分期计划

> 版本：v1.5（2026-10-08 第二批补强：H5 模型/模板切换接线 + h5-smoke AI 段 22 项 + SSE nginx 缓冲/多知识域向量分组修复）｜日期：2026-10-08｜状态：已实施，§9.2 未决项全部定案，§10.6-1/-4 已闭环
> 关联：[02](02-requirements.md)、[03](03-architecture.md)、[04](04-database-design.md)、[05](05-api-spec.md)、[10](10-module-plan-index.md)、[20](20-completion-review.md)、[23](23-module-plan-h5.md)

## 1. 背景与定位

AgentOA 已交付 8 大业务模块（96% 完成度），但全代码库零 AI 能力。本模块引入"配置厂商 + 多模态模型 Key 即可获得 AI 能力"的模型接入层，并在此之上分期交付对话助手、知识库问答、业务 Copilot 与 Agent/MCP，使 AgentOA 成为真正的 AI Agent OA。

### 1.1 开源调研结论（能力积木）

| 积木 | 参考项目 | 采纳方式 |
|---|---|---|
| 厂商/Key 渠道管理（测试连接、模型映射） | one-api、new-api、LiteLLM | M1 直接借鉴渠道数据模型与测试按钮 UX |
| Java LLM 抽象（Chat/Memory/Tool/RAG/MCP） | LangChain4j、Spring AI | 选型 **LangChain4j**（OpenAI 兼容适配 + 多模态 + MCP） |
| RAG 管线（解析→分块→向量化→检索测试→引用） | Dify、RAGFlow、MaxKB、FastGPT | M3 借鉴分块/检索测试 UI/引用溯源 |
| Agent/MCP 工具生态 | MCP servers、OpenAI Agents SDK | M5 借鉴工具注册与多步执行 |
| AI+管理后台一体化形态 | ruoyi-ai（RuoYi+LangChain4j，最接近蓝图）、ruoyi-vue-pro AI 模块 | 模块组织、菜单形态、聊天 UI |

### 1.2 已确认决策

| 决策点 | 结论 |
|---|---|
| 首期范围 | M1+M2（模型配置 + 对话助手），M3–M5 需求先行固化、分期实施 |
| LLM 接入 | **LangChain4j** + `langchain4j-open-ai`（OpenAI 兼容协议覆盖 DeepSeek/Qwen/Kimi/GLM/Ollama 等） |
| Key 存储 | **双通道**：AES-256-GCM 加密存库（脱敏回显）+ `configtree:/run/secrets`/环境变量覆盖（secrets 优先） |
| 用量治理 | 调用日志 + 按人/角色 日/月 token 与次数限额 |

## 2. 总体架构与公共约定

### 2.1 模块落点

- 新模块 `ruoyi-modules/agentoa-ai`，包 `org.dromara.agentoa.ai.{controller,domain/{bo,vo,enums},mapper,service/impl}`，与既有 8 业务模块同构。
- 依赖：`langchain4j`、`langchain4j-open-ai`（版本锁定当前 1.x 稳定版，随 BOM 管理）；复用 `ruoyi-common-core/web/security/satoken/redis/mybatis/oss/sse/log/encrypt/idempotent`。
- 迁移：`agentoa-ai/src/main/resources/db/migration`，从 **V28** 起按阶段独立迁移（`V28__ai_foundation.sql`、`V29__ai_chat.sql`、`V30__ai_rag.sql`、`V31__ai_copilot.sql`、`V32__ai_agent.sql`），只追加不回改。
- 表前缀 `oa_ai_`；字典前缀 `ai_`；菜单号段 **3000–3099**；权限串前缀 `ai:`。

### 2.2 模型接入抽象（贯穿 M1–M5）

```
业务层(M2-M5) → AiModelRouter(按模型路由/降级) → LlmGateway(限额/日志/重试) → LangChain4j ChatModel
                                                                    ↑
                                        ProviderRegistry(渠道配置, Key 双通道解析)
```

- 所有模型调用（对话、embedding、rerank、Copilot、Agent）**必须**走 `LlmGateway` 统一入口，禁止业务代码直连 HTTP；保证限额、日志、脱敏、超时口径一致。
- Key 解析顺序：`secret_ref`（环境变量/configtree 名）→ 库中 `api_key_cipher` 解密 → 无则渠道不可用。
- 多模态：模型 `capability` 含 `vision` 才允许图片输入，否则 400 `MODEL_CAPABILITY_MISMATCH`。

### 2.3 安全红线

1. API Key 明文**永不回显、永不落日志**（日志脱敏复用 `ruoyi-common-core` 敏感字段处理）；前端仅见 `sk-***ab12` 形态 hint。
2. 渠道增删改与测试连接走 `@Log` 审计；查看 Key 列表需 `ai:provider:query`（默认仅管理员）。
3. 对话/知识内容按用户隔离，列表与详情服务端过滤（对象权限模式同知识库：无权限即 404）。
4. 提示词模板与 Copilot 输出为**辅助建议**，UI 明示"AI 生成内容仅供参考"；M4 写操作一律人工确认后落库，AI 不直接修改业务数据。
5. 出站调用仅允许配置的 `base_url`，禁止模板/用户输入控制出站地址（防 SSRF）。

### 2.4 菜单与权限规划（3000–3099）

| 菜单 | ID | 权限串 |
|---|---|---|
| AI 助手（目录） | 3000 | — |
| ├ AI 对话 | 3001 | `ai:chat:use`（3010 按钮） |
| ├ 知识问答（M3） | 3002 | `ai:qa:use` |
| └ 业务助手（M4） | 3003 | `ai:copilot:use` |
| AI 管理（目录） | 3020 | — |
| ├ 模型渠道 | 3021 | `ai:provider:query/add/edit/remove/test` |
| ├ 模型管理 | 3022 | `ai:model:query/add/edit/remove` |
| ├ 提示词模板 | 3023 | `ai:prompt:query/add/edit/remove` |
| ├ 用量与配额 | 3024 | `ai:usage:list/export`、`ai:quota:list/edit` |
| ├ 知识库管理（M3） | 3025 | `ai:kb:query/add/edit/remove/index` |
| └ 工具与 MCP（M5） | 3026 | `ai:tool:query/add/edit/remove/test`、`ai:agent:*` |

字典：`ai_provider_type`（openai/deepseek/qwen/moonshot/zhipu/gemini/anthropic/ollama/custom）、`ai_model_capability`（chat/vision/embedding/rerank）、`ai_msg_role`、`ai_usage_status`、`ai_quota_scope`（user/role）、`ai_quota_period`（day/month）、`ai_kb_index_status`、`ai_copilot_task`、`ai_tool_type`（function/mcp）。

## 3. M1 模型接入层（配 Key 即 AI 地基）

**目标**：管理员配置厂商渠道与模型后，系统具备统一、可治理的多模态模型调用能力，M2–M5 全部构建于此。

### 3.1 功能需求

| 编号 | 需求 | 说明 |
|---|---|---|
| AI-M1-01 | 渠道管理 | `oa_ai_provider`：名称、厂商类型（字典）、`base_url`、API Key、`secret_ref`、启用状态、优先级、备注。CRUD + 启停；删除时若被模型引用则 409 |
| AI-M1-02 | Key 双通道存储 | 入库 AES-256-GCM 加密（复用 `ruoyi-common-encrypt` 方案，同薪资口径）；`secret_ref` 指向环境变量/`configtree:/run/secrets` 时优先使用；回显仅 hint（后 4 位） |
| AI-M1-03 | 测试连接 | `POST /providers/{id}/test`：发最小请求（可配测试 prompt），返回连通性、延迟、错误分类（鉴权失败/超时/余额/模型不存在），不落业务数据 |
| AI-M1-04 | 模型管理 | `oa_ai_model`：所属渠道、`model_key`、别名、能力（chat/vision/embedding/rerank 多选）、上下文窗口、默认 temperature/max_tokens、启用、全局默认模型标记 |
| AI-M1-05 | 模型路由 | `AiModelRouter`：按显式指定或默认模型选择渠道；同模型多渠道按优先级+健康度选择，失败自动切换备用渠道（最多 2 次），仍失败返回 `LLM_UPSTREAM_ERROR` |
| AI-M1-06 | 统一网关 | `LlmGateway`：超时（连接 10s / 读 120s / 流式空闲 60s）、重试（仅幂等场景 1 次）、异常归一化错误码 |
| AI-M1-07 | 用量日志 | `oa_ai_usage_log`：用户、渠道、模型、业务类型（chat/rag/copilot/agent）、prompt/completion/total tokens、耗时、状态、错误信息、conversation/任务 ID；异步落库不阻塞主流程 |
| AI-M1-08 | 配额管理 | `oa_ai_quota`：主体（用户/角色）、周期（日/月）、token 限额、请求次数限额、启用；判定在网关入口，超限 429 `AI_QUOTA_EXCEEDED`；未配置配额的主体默认放行（可配全局兜底限额） |
| AI-M1-09 | 用量统计 | 按用户/模型/日聚合查询与导出（FastExcel），展示 token 曲线 |

### 3.2 数据表（V28__ai_foundation.sql）

- `oa_ai_provider`：id, name, provider_type, base_url, api_key_cipher, api_key_hint, secret_ref, priority, enabled, remark, create_by/time, update_by/time, deleted
- `oa_ai_model`：id, provider_id, model_key, alias, capability(json), context_window, default_temperature, max_tokens, enabled, is_default, remark, 标准审计字段
- `oa_ai_usage_log`：id, user_id, username, provider_id, model_key, biz_type, conversation_id, task_id, prompt_tokens, completion_tokens, total_tokens, latency_ms, status, error_code, error_msg, create_time
- `oa_ai_quota`：id, scope_type, scope_id, scope_name, period_type, token_limit, request_limit, enabled, 审计字段；唯一约束（scope_type, scope_id, period_type）

### 3.3 API（前缀 `/api/v1/ai`，envelope 同全局）

- `GET/POST /providers`、`GET/PUT/DELETE /providers/{id}`、`PUT /providers/{id}/status`、`POST /providers/{id}/test`
- `GET/POST /models`、`GET/PUT/DELETE /models/{id}`、`GET /models/enabled`（登录即可，供选择器）
- `GET /usage/stats`、`GET /usage/logs`、`GET /usage/export`
- `GET/POST /quotas`、`PUT/DELETE /quotas/{id}`

### 3.4 测试与退出条件

H2 测试：Key 加解密与脱敏回显、secret_ref 优先级、测试连接错误分类、路由降级、限额拦截（用户/角色/日/月/次数）、用量日志异步落库、越权访问 403、删除被引用渠道 409、Key 不出现在日志/响应（断言扫描）。退出条件：配置 1 个真实渠道 + 1 个 mock 渠道，测试连接通过，限额与日志闭环验证，`smoke.mjs` 新增 AI-M1 契约通过。

## 4. M2 AI 助手对话（多模态对话）

**目标**：全员可在 OA 内与多模态模型流式对话、传图识别，配置 Key 后即"变成 AI OA"。

### 4.1 功能需求

| 编号 | 需求 | 说明 |
|---|---|---|
| AI-M2-01 | 会话管理 | `oa_ai_conversation`：本人可见；创建/列表/重命名/删除（软删）；首条消息自动命名；会话绑定默认模型与提示词模板 |
| AI-M2-02 | 消息与记忆 | `oa_ai_message`：role（user/assistant/system）、内容、附件、tokens、状态；上下文窗口按模型 `context_window` 截断（保留系统提示 + 最近 N 轮）；持久化即记忆来源，LangChain4j `ChatMemory` 从表装配 |
| AI-M2-03 | 流式输出 | SSE（`text/event-stream`，绕过 envelope）逐 token 推送；事件：`delta/done/error/usage`；前端打字机渲染；**停止生成**（服务端取消上游调用，消息标记 `stopped`） |
| AI-M2-04 | 多模态输入 | 图片上传（≤5 张，单张 ≤10 MiB，jpg/png/webp，复用 `sys_file` + 私有 S3 + 三重校验）；vision 模型以 image content 参与推理；非 vision 模型 400 `MODEL_CAPABILITY_MISMATCH` |
| AI-M2-05 | 提示词模板 | `oa_ai_prompt_template`：预置"公文写作/邮件润色/翻译/报表解读/会议纪要"等；会话可选模板注入 system prompt；管理员可维护 |
| AI-M2-06 | 模型切换 | 会话内切换启用模型；历史消息不重算；切换后新消息用新模型 |
| AI-M2-07 | 失败与重试 | 上游失败消息级提示 + 单条"重新生成"（产生新 assistant 消息，保留旧版本）；限额超限、能力不匹配、超时分别给出可读提示 |
| AI-M2-08 | 前端页面 | `views/ai/chat`（左会话列表 + 右对话流）；`views/ai/{provider,model,prompt,usage}` 管理页；`api/ai/*` |
| AI-M2-09 | 治理接入 | 对话走 `LlmGateway`：限额、用量日志（biz_type=chat）、审计；附件下载走私有文件授权 |

### 4.2 数据表（V29__ai_chat.sql）

- `oa_ai_conversation`：id, user_id, title, model_id, prompt_template_id, status, last_message_time, 审计字段
- `oa_ai_message`：id, conversation_id, role, content, attachments(json: file_id 列表), prompt_tokens, completion_tokens, status(streaming/done/stopped/error), error_code, 审计字段
- `oa_ai_prompt_template`：id, code, name, category, content, enabled, is_builtin, 审计字段；唯一约束（code）

### 4.3 API

- `GET/POST /chat/conversations`、`GET/PUT/DELETE /chat/conversations/{id}`
- `GET /chat/conversations/{id}/messages`
- `POST /chat/completions`（SSE 流式；入参 conversation_id/model_id/prompt_template_id/content/attachment_ids）
- `POST /chat/messages/{id}/stop`、`POST /chat/messages/{id}/regenerate`
- `GET /chat/templates`（登录即可）、管理端 `POST/PUT/DELETE /prompt-templates`

### 4.4 测试与退出条件

H2 测试：会话/消息越权（他人会话 404）、上下文截断、停止生成状态机、重生成版本留存、附件类型/大小/魔数校验、非 vision 模型拒图、SSE 事件序列（delta→done→usage）、限额中途超限中断提示、Key 不泄露。冒烟：`smoke.mjs` SSE 契约 + `browser-smoke.mjs` 聊天页渲染与流式对话（mock 上游）。退出条件：真实渠道下多轮对话 + 传图识别跑通，`build:prod` + `vue-tsc` 通过。

## 5. M3 知识库问答（RAG）

**目标**：对知识库文档/制度文件提问，回答带引用溯源。

### 5.1 功能需求

| 编号 | 需求 | 说明 |
|---|---|---|
| AI-M3-01 | 知识域管理 | `oa_ai_kb`：名称、描述、可见性（私有/指定人/全员）、embedding 模型、状态；成员授权复用知识库 ACL 思路（授权过滤在服务端） |
| AI-M3-02 | 数据源接入 | 两类来源：① 知识库文档 `oa_document`（取 `content_text`/版本正文）② 直传文件（PDF/DOCX/TXT/MD，经 `sys_file`）；`oa_ai_kb_source` 记录索引状态（pending/indexing/ready/failed）与进度 |
| AI-M3-03 | 解析与分块 | 文本清洗（Markdown 去标记）→ 分块（默认 512 token、64 token 重叠，按标题/段落边界优先）→ embedding → 存储；增量：文档新版本触发重索引，删除触发失效 |
| AI-M3-04 | 向量存储 | **P0 方案**：`oa_ai_kb_chunk` 存 embedding BLOB + 应用内余弦相似度（带热缓存，Redis），满足 10–200 人规模；预留 `store_type` 字段与接口抽象，后续可切换专用向量库（见 §9 未决项） |
| AI-M3-05 | 检索问答 | 混合检索：向量 top-k（默认 5）+ 关键词 LIKE 候选 → 简单融合排序 → 注入提示词 → 流式回答；回答末尾输出引用列表（文档名 + 章节/片段 + 跳转链接），点击跳原文 |
| AI-M3-06 | 检索测试 | 管理端检索测试面板（输入问题→查看命中片段与相似度分布），FastGPT 模式；分块预览与手工调整 |
| AI-M3-07 | 权限与隔离 | 检索范围 = 提问人对源文档/知识域的可见范围（服务端过滤，禁止先查全量后过滤）；跨域提问 403/空结果；引用片段不得越权泄露原文 |
| AI-M3-08 | 页面 | `views/ai/qa`（问答+引用）、`views/ai/kb`（知识域/数据源/索引状态/检索测试） |

### 5.2 数据表（V30__ai_rag.sql）

- `oa_ai_kb`：id, name, description, visibility, member_scope(json), embedding_model_id, status, 审计
- `oa_ai_kb_source`：id, kb_id, source_type(document/file), doc_id, file_id, title, chunk_count, index_status, error_msg, indexed_at, 审计
- `oa_ai_kb_chunk`：id, kb_id, source_id, seq, heading, content, token_count, embedding(blob), store_type, 审计

### 5.3 API

- `GET/POST /kb`、`GET/PUT/DELETE /kb/{id}`、`GET/POST /kb/{id}/members`、`DELETE /kb/{id}/members/{userId}`
- `GET/POST /kb/{id}/sources`、`DELETE /kb/{id}/sources/{sourceId}`、`POST /kb/{id}/sources/{sourceId}/reindex`
- `POST /kb/{id}/search-test`（管理端）、`GET /kb/{id}/chunks`（分块预览）
- `POST /qa/ask`（SSE 流式；入参 kb_ids/query/conversation_id 可选）、`GET /qa/history`

### 5.4 测试与退出条件

H2 测试：分块边界与重叠、增量重索引、检索权限过滤（越权知识域不命中）、引用片段权限、索引失败可重试、删除源后不可检索、embedding 模型未配置报错。退出条件：真实文档问答引用可跳转且不越权，检索测试面板可用。

## 6. M4 业务 Copilot（场景化 AI）

**目标**：AI 嵌入既有业务动作，提供摘要、起草、解读、智能填写；**只出建议，不落业务库**（人工确认/复制后生效）。

### 6.1 功能需求（每个能力独立开关，管理员可配置启用）

| 编号 | 场景 | 说明 |
|---|---|---|
| AI-M4-01 | 审批摘要 | 流程详情页"AI 摘要"：汇总表单关键字段、历史意见、耗时，输出要点 + 风险提示；只读流程数据，结果不落流程表 |
| AI-M4-02 | 报表解读 | 报表看板"AI 解读"：输入指标口径与数值（服务端组装，不传原始明细），输出趋势/异常解读文案 |
| AI-M4-03 | 公文/公告起草 | 公告编辑页"AI 起草"：按模板（通知/通报/会议纪要）生成初稿，插入编辑器后人工修改发布 |
| AI-M4-04 | 智能填写 | 请假/报销创建页"AI 帮填"：据用户自然语言（"下周三到周五调休"）生成表单建议值（JSON），前端预填，提交仍走原表单校验与审批 |
| AI-M4-05 | 会议纪要 | 日程/会议结束后粘贴录音转写或要点，生成纪要草稿存入日程备注（人工保存） |
| AI-M4-06 | 任务编排 | `oa_ai_copilot_task`：异步任务（长摘要/长文生成），走既有 `@Scheduled`+DB 租约或 SnailJob；完成经 outbox/WS 通知；失败可重试 |

### 6.2 数据表（V31__ai_copilot.sql）

- `oa_ai_copilot_config`：id, scene_code, enabled, model_id, prompt_template_id, 审计
- `oa_ai_copilot_task`：id, scene_code, biz_type, biz_id, user_id, input_ref(json), output, status(pending/running/done/failed), error_msg, tokens, 审计

### 6.3 API

- `GET /copilot/scenes`（登录即可）、管理端 `PUT /copilot/scenes/{code}`（启停/换模型/换模板）
- `POST /copilot/{scene}`（同步 SSE 生成；如 `/copilot/approve-summary`、`/copilot/notice-draft`、`/copilot/form-suggest`、`/copilot/report-insight`、`/copilot/minutes`）
- `GET /copilot/tasks`、`POST /copilot/tasks/{id}/retry`

### 6.4 测试与退出条件

H2 测试：场景开关关闭即 403、输入数据最小化（断言不取无关敏感字段）、建议值不落业务表（快照断言）、越权流程摘要 404、异步任务状态机与重试。退出条件：5 个场景各跑通一例，业务数据零污染审计通过。

## 7. M5 Agent 与 MCP（工具化智能体）

**目标**：模型可调用 OA 内部工具与外部 MCP 服务，多步完成任务（如"查我本周待办并起草催办通知"）。

### 7.1 功能需求

| 编号 | 需求 | 说明 |
|---|---|---|
| AI-M5-01 | 内置函数工具 | `oa_ai_tool`（type=function）：待办查询、日程查询、请假余额、报销进度、知识检索（M3）、公告搜索、通讯录查询等；每个工具 = Java 方法 + JSON Schema + 权限声明 |
| AI-M5-02 | MCP 接入 | `oa_ai_tool`（type=mcp）：登记外部 MCP server（URL/stdio 配置、鉴权头），发现并导入工具列表；LangChain4j MCP client |
| AI-M5-03 | Agent 配置 | `oa_ai_agent`：名称、系统提示词、绑定模型、工具集、最大步数（默认 6）、超时；预置"办公助手"等内置 Agent |
| AI-M5-04 | 执行与可观测 | `oa_ai_agent_run`：输入、逐步轨迹（工具调用/参数/结果摘要）、输出、tokens、状态；UI 可展开执行轨迹 |
| AI-M5-05 | 权限与安全 | 工具执行带调用者身份，**工具内部照常走对象权限**（越权即工具报错回传模型）；写操作工具（如创建待办）需 `ai:tool:write` + 人工确认后执行；单轮工具调用次数与总耗时熔断 |
| AI-M5-06 | 入口 | AI 对话页可选 Agent 模式；`views/ai/tools`（工具/MCP 管理、连通性测试）、`views/ai/agents`（Agent 配置、运行记录） |

### 7.2 数据表（V32__ai_agent.sql）

- `oa_ai_tool`：id, code, name, type(function/mcp), schema(json), endpoint/config(json), enabled, write_flag, 审计
- `oa_ai_agent`：id, code, name, system_prompt, model_id, tool_codes(json), max_steps, timeout_sec, enabled, is_builtin, 审计
- `oa_ai_agent_run`：id, agent_id, user_id, conversation_id, input, trace(json), output, status, total_tokens, duration_ms, 审计

### 7.3 API

- `GET/POST /tools`、`GET/PUT/DELETE /tools/{id}`、`POST /tools/{id}/test`、`GET /tools/discover/{mcpServerId}`
- `GET/POST /agents`、`GET/PUT/DELETE /agents/{id}`、`POST /agents/{id}/run`（SSE）、`GET /agents/runs`、`GET /agents/runs/{id}`

### 7.4 测试与退出条件

H2 测试：工具权限回落（员工查不到他人数据）、写工具需确认、步数/超时熔断、MCP server 不可达降级、轨迹可审计、注入攻击（工具参数伪造）防护。退出条件：内置 Agent 完成"查待办→起草通知"跨工具任务，轨迹可回放。

## 8. 非功能需求（全阶段适用）

| 项 | 要求 |
|---|---|
| 性能 | 流式首 token P95 ≤ 3s（上游正常时）；对话接口不因日志/限额阻塞（异步）；embedding 索引不占用 Web 线程（独立线程池） |
| 可靠 | 上游故障降级提示，不影响 OA 主流程；AI 失败不得导致业务回滚失败；网关超时/重试口径统一 |
| 可观测 | `oa_ai_usage_log` 为审计与容量依据；provider 测试与工具测试提供诊断；关键异常入日志（脱敏） |
| 容量 | 10–200 人；单日 10 万 token 级；RAG 文档 5000 篇内 BLOB 方案可支撑（超限转入 §9 未决项评估） |
| 兼容 | 多租户关闭现状不变；H2 测试与 MySQL 8.4 兼容（embedding blob 二进制列）；`build:prod` + `vue-tsc` 必过 |

## 9. 里程碑、依赖与未决项

### 9.1 里程碑与依赖

| 阶段 | 前置 | 迁移 | 交付 | 验收口径 |
|---|---|---|---|---|
| M1 | 无 | V28 | 渠道/模型/网关/限额/日志 | 配 Key→测试连接→限额与日志闭环 |
| M2 | M1 | V29 | 对话/流式/多模态/模板 | 真实多轮对话 + 传图识别 |
| M3 | M1（embedding 模型） | V30 | RAG 问答/检索测试 | 引用可跳转不越权 |
| M4 | M2（+M3 可选） | V31 | 5 个场景 Copilot | 业务数据零污染 |
| M5 | M2（+M3 工具） | V32 | 工具/MCP/Agent | 跨工具任务 + 轨迹回放 |

每阶段按"迁移+契约 → 后端服务 → 前端页面 → 测试/冒烟"节奏，验收通过才进下一阶段（同 [10](10-module-plan-index.md) 统一完成定义）。

**实施记录（2026-10-06）**：M1–M5 全部落地。迁移 V28–V32 已执行；后端 `ruoyi-modules/agentoa-ai`（`LlmGateway` 统一出口 + `CopilotBizReader` 只读摘要 SPI + `AgentTool`/`McpToolGateway`/`AgentEngine` 多步执行）；前端 `views/ai/{chat,qa,kb,provider,model,prompt,usage,copilot,tool,agent}` + `components/AiCopilotDrawer`（嵌入 5 个业务页）；H2 契约测试 66 例全绿，`scripts/smoke.mjs` 覆盖 M1–M5 契约，`scripts/browser-smoke.mjs` 覆盖新页面渲染。

### 9.2 未决项（2026-10-08 全部定案）

1. **M3 向量存储**：**定案**——保留 MySQL BLOB + 应用内余弦 P0 方案；切换触发条件（任一满足即立项）：单库 chunk 总量 > 100 万、或检索 P95 > 500ms、或文档总数 > 5000 篇；后继选型 **Redis Stack 首选**（复用既有 Redis 运维栈），千万级向量以上再评估 Milvus；pgvector 排除（与 MySQL 主库异构引入 PG 运维负担）。阈值度量支撑已落地：`AiKbVo.chunkCount`（知识域分块总量）+ `KbRetriever` 检索遥测日志（扫描分块/候选/命中/耗时）。
2. **M2 聊天 UI 组件**：**定案——不引入 element-plus-x，FE-F5-01 关闭**。自研 chat 页已覆盖流式/停止/重生成/附件/Agent 轨迹/模板切换，XSS 协议白名单刚加固；第三方聊天组件需重做安全审定且无 Agent 轨迹对应能力，收益低风险高。后续仅当需要虚拟滚动长会话等能力时再按需评估。
3. **uniapp 移动端 AI**：维持不纳入 M1–M5（后端不新增模块、复用 M2/M3），由 [23](23-module-plan-h5.md) H6 承接；端侧需求细化、API 对齐与实施记录见本文档 **§10**。
4. **LangChain4j 版本**：维持 BOM `1.12.2` 统一管理。**更正（2026-10-08）**：`dependency:list` 实测 `langchain4j-mcp` 解析为 `1.12.2-beta22`（MCP 模块 beta 版号，即由 BOM 管理），`McpToolGateway` 已使用 `StreamableHttpMcpTransport` + `StdioMcpTransport`，无能力缺口；上轮偏差登记「未使用 1.12.2-beta22」表述有误。不单独锁定版本、不引入额外 beta 分支；如需 MCP 新协议能力（OAuth/采样等）随 LangChain4j 正式版升级解决。
5. **默认限额口径**：已定值 100000 token / 500 次（见下方偏差登记）。

**实现期偏差登记（2026-10-07 复核）**

- §9.2-4 表述与代码有出入：`agentoa-ai/pom.xml` 的 `langchain4j-mcp` 未单独指定版本，由 `langchain4j-bom 1.12.2` 统一管理（未使用 `1.12.2-beta22`）；如需 beta MCP 能力再单独立项评估。
- §9.2-5 已在代码定值：`AiQuotaGuard` 全局兜底为人均日 100000 token / 500 次请求（`agentoa.ai.quota.fallback-token-limit` / `fallback-request-limit` 可配），超限 429 `AI_QUOTA_EXCEEDED`。
- `ai_model_capability` 字典含 `rerank`，但 M1–M5 无 rerank 传输层/管线（仅能力标签，检索为向量+关键词融合），属声明预留。
- V28–V32 无 `migration-undo` 回滚脚本（与「只追加不回改」口径一致，同 foundation V1–V3）。

**契约回归暴露项（2026-10-07 已修复）**

以下为跑通 `smoke.mjs` 全链路时发现的 M1–M5 契约偏差，均已修复并补回归用例：

| 偏差 | 违反条款 | 修复 |
|---|---|---|
| SSE 响应未声明 `text/event-stream`（实为 `application/json`），且 `emitter.complete()` 触发的 ASYNC 分派重跑鉴权链抛 `SaTokenContextException`，被全局异常器把 JSON 信封写进事件流尾部 | §4.2「SSE（`text/event-stream`，绕过 envelope）」 | 5 个 SSE 端点（`chat/completions`、`chat/messages/{id}/regenerate`、`qa/ask`、`copilot/{scene}`、`agents/{id}/run`）声明 `produces = TEXT_EVENT_STREAM_VALUE` 并显式设置响应类型；`AccountGuard` 对 `DispatcherType.ASYNC` 短路（应用内无 `Callable`/`DeferredResult` 端点，ASYNC 分派仅出现在 SSE 完成路径） |
| `GET /chat/templates` 返回全部启用模板（含 Copilot 场景专用 `copilot_*`），会话模板选择器出现「报表解读/会议纪要」重名项 | §4.1 AI-M2-05「会话可选模板」 | 按 `oa_ai_copilot_config.prompt_template_id` 绑定关系过滤场景专用模板（管理端 `/prompt-templates` 仍可见全量） |
| `GET /kb`、`GET /models` 无过滤参数时 NPE 500（MyBatis-Plus `.like/.eq` 的 value 参数急需求值，`getName().strip()` 空指针） | §5.3 列表契约 | 补空值护栏；新增 `AiKbIndexH2Test#listWithoutNameFilterReturnsVisibleKbs`、`AiGatewayH2Test#modelPageWithoutKeyFilterReturnsAll` |
| MCP 建连失败（`DefaultMcpClient.build()` 阶段）抛裸 `RuntimeException` 500，未归一化 | §2.2「异常归一化」、§7.4「MCP server 不可达降级」 | `McpToolGateway.open()` 统一映射 `AI_MCP_CONNECT_FAILED` 502（调用阶段仍映射 `AI_MCP_CALL_FAILED`） |
| `KbRetriever.search` 在可见源为空时短路 embedding 调用，检索测试/问答无法暴露上游错误（返回空 200 而非 `LLM_UPSTREAM_ERROR` 502） | §2.2 错误归一化、§5.1 AI-M3-05 | 查询向量化提到可见性短路之前；空知识域仍返回空命中，仅在 embedding 上游故障时如实报错 |
| 会话绑定的 `model_id` 指向已删除/停用模型时，`POST /chat/completions` 与重生成一律 404 `AI_MODEL_NOT_FOUND`，会话从此不可用 | §4.1 AI-M2-06「会话内切换启用模型」 | 会话绑定的历史模型不可解析时回退全局默认模型自愈（`AiChatServiceImpl.routesForConversation`，`chat` 非显式路径与 `regenerate` 共用）；**显式** `model_id` 仍严格校验 404，API 契约不变 |

**代码复核补强（2026-10-08）**

对 M1–M5 逐条复核后补齐下列缺口（均为补实现/补回归，不改 API 契约）：

| 缺口 | 违反条款 | 处置 |
|---|---|---|
| `AiQuotaGuard.checkMidStream` 已定义但无调用点，§4.4「限额中途超限中断提示」实际未接线 | §4.4 测试项、AI-M1-08 | `LlmGateway` 流式 `onDelta` 按累计产出（每 256 token / 16 个 delta）复检限额，超限即 `abort()` 上游输出并回传 429 `AI_QUOTA_EXCEEDED（流式生成已中断）`；`StreamTicket.abort()` 区分系统熔断与用户「停止生成」（消息状态 error vs stopped）；部分产出按 `completion_tokens` 记入用量日志（限额不再漏计） |
| 「补回归用例」口径不完全成立：`chat/templates` 场景过滤、MCP 建连归一化、`KbRetriever` 向量化顺序、`AccountGuard` ASYNC 短路四项无专属用例 | §3.4/§4.4/§5.4/§7.4 测试项 | 新增回归用例：`AiChatH2Test#chatTemplateListHidesCopilotSceneTemplates`、`AiAgentToolH2Test#mcpConnectFailureIsNormalizedTo502`、`AiKbIndexH2Test#searchSurfacesEmbeddingFailureBeforeVisibilityShortCircuit`、`agentoa-foundation` `AccountGuardTest#asyncDispatchShortCircuitsWithoutTouchingAuthChain` |
| 月周期限额、Agent 步数/超时熔断无专属用例 | §3.4、§7.4 | 新增 `AiGatewayH2Test#monthlyQuotaIgnoresPreviousMonthUsage`（上月用量不计入月周期）、`AiAgentToolH2Test#runCircuitBreaksWhenMaxStepsExceeded` / `#runCircuitBreaksWhenDeadlinePasses` |
| `McpToolGateway.open()` 仅归一化 `DefaultMcpClient.build()` 阶段异常，transport 构建阶段（如非法 URL）仍裸抛 500 | §2.2「异常归一化」 | 归一化范围扩大到 transport 构建 + 客户端构建全程（`AI_MCP_CONFIG_INVALID` 400 原样透传） |
| M4 异步任务机制简化：主路径同步 SSE + 仅 retry 走进程内线程池，未按 §6.1 AI-M4-06 用 `@Scheduled`+DB 租约/SnailJob | §6.1 AI-M4-06 | **已定案（2026-10-08）**：最小补强而非全量改造——新增 `CopilotTaskRecovery` 启动回收孤儿任务 + `retry()` CAS 领取/执行侧二次 CAS（见下方「M4 最小补强与 M3 可观测」），主路径保持同步 SSE；完整 `@Scheduled`+DB 租约/SnailJob 异步化设触发条件再立项（任务平均耗时 > 30s 或日任务量 > 100 件）。任务表与状态机、失败可重试、outbox 通知不变 |

H2 用例总数 69 → **77**（另有 foundation `AccountGuardTest` 1 例）；`smoke.mjs` 回归 **493** 项通过，`browser-smoke.mjs` 通过。

**前端侧补强（2026-10-08，随 [22](22-module-plan-frontend.md) §7.5 一并交付）**

| 缺口 | 违反条款 | 处置 |
|---|---|---|
| AI-M2-04 图片约束（≤5 张、≤10 MiB、jpg/png/webp）仅服务端校验，前端选完文件即发起上传，超限由服务端 400 拒回，体验差且白耗请求 | §4.1 AI-M2-04 | 新增 `agentoa-frontend/src/utils/upload.ts` `checkChatImage` 与服务端 `AiAttachmentRules` 同口径前置拦截，`views/ai/chat/index.vue` 接 `before-upload`；`handleUpload` 补失败分支（原仅 `onSuccess`，请求失败无提示）。内容魔数校验仍归服务端三重校验 |
| `views/ai/chat` 上传按钮为纯图标按钮，无可及名称 | §2.3、[22](22-module-plan-frontend.md) §5 无障碍 | 补 `aria-label`「上传图片（最多 5 张，单张不超过 10 MiB）」 |
| 22 号 §5「AI markdown 渲染防 XSS（白名单标签/转义）」实测缺 URL 协议白名单（`utils/markdown.ts` 仅转义），`[x](javascript:...)` 可穿透 | §2.3 安全红线 | `utils/markdown.ts` 新增 `safeUrl` 协议白名单（`http/https/mailto/tel` + 相对路径 + 图片侧栅格 `data:image/*`，抹 C0 控制符防 `java\nscript:`/`%6aavascript:` 伪装），非法 URL 降级为纯文本 |

回归用例随 22 号 §7.5 落 `src/utils/__tests__/{markdown,upload}.test.ts`（20 例），前端 vitest 总数 77 → 97。

**M4 最小补强与 M3 可观测（2026-10-08，随 §9.2 未决项定案一并交付）**

| 缺口 | 违反条款 | 处置 |
|---|---|---|
| 进程中断遗留 pending/running 任务无人继续执行（同步 SSE 主路径与重试执行池均为进程内），任务状态机永久卡死 | §6.1 AI-M4-06「失败可重试」 | 新增 `service/support/CopilotTaskRecovery`（`ApplicationRunner`）：启动时 `pending/running → failed`（error_msg=进程中断回收，可重新生成），可经既有 retry 重试；与 `RETRY_EXECUTOR` 同为单节点部署口径 |
| `retry()` 无并发防护：重复点击/并发提交对同一任务重复执行 | §6.4 任务状态机 | `retry()` 改 CAS 领取（仅 `done/failed → pending`，0 行即 409 `AI_COPILOT_TASK_CONFLICT`）+ 执行侧 `pending → running` 二次 CAS（任务被删除/回收时跳过执行，不覆盖他人写入）；队列满异常回滚领取，任务仍可重试 |
| §9.2-1 向量库切换阈值缺少度量口径 | §9.2-1 | `AiKbVo` 增 `chunkCount`（知识域分块总量，同 `sourceCount` 聚合口径）；`KbRetriever.search` 输出遥测日志（扫描分块/候选/命中/耗时 ms），供切换阈值（chunk 总量、检索 P95）判断 |

H2 用例 77 → **80**（`AiCopilotH2Test#orphanInFlightTasksAreRecoveredOnStartup`、`#retryClaimsOnlyTerminalTasks`、`AiKbIndexH2Test#listExposesSourceAndChunkCounts`）；`mvn -pl ruoyi-admin -am verify` BUILD SUCCESS，`smoke.mjs` 回归 **498** 项通过。

**端侧 H5 冒烟补强暴露项（2026-10-08 已修复）**

随 §10.6-4 `h5-smoke.mjs` AI 段（mock 上游慢速流/异构向量模型）实测暴露两处后端缺陷：

| 缺口 | 违反条款 | 处置 |
|---|---|---|
| 5 个 SSE 端点未声明 `X-Accel-Buffering: no`，部署形态下 nginx `proxy_buffering` 默认开启把 delta 攒到流结束才下发——打字机逐 token 推送实际不生效（mock 慢速流实测 4s 内无 delta，仅 `done` 全量内容可见），本地 dev 直连后端时被掩盖 | §4.1 AI-M2-03「SSE 逐 token 推送；前端打字机渲染」 | `AiChatController`（completions/regenerate）、`AiQaController`、`AiCopilotController`、`AiAgentController` 5 个 SSE 端点补 `X-Accel-Buffering: no`（nginx 据此关闭该响应缓冲，PC/H5/小程序同修）；h5-smoke 以 mock 慢速流断言首字可见后可停止 |
| `KbRetriever.search` 多知识域混检只按**首个**知识域的向量模型给查询向量化：异构向量模型跨模型算相似度（静默错分），且任一知识域向量模型不可达时拖垮全部问答 | §5.1 AI-M3-04/AI-M3-05 | 查询向量化按知识域绑定向量模型分组各查各的（`groupKbsByEmbeddingModel`，`resolveEmbeddingModelId` 退役），embedding 上游故障仍如实报错（§9.2 契约修复口径不变）；回归用例 `AiQaRagH2Test#queryEmbeddingRunsPerKbEmbeddingModel` |

H2 用例 80 → **81**；`mvn -pl ruoyi-admin -am verify` BUILD SUCCESS，`smoke.mjs` 回归 **500** 项通过，`browser-smoke.mjs` 通过（axe 基线 4/11/12/10/9 不变）。

### 9.3 文档回写清单

本计划同步更新：[02](02-requirements.md)（AI 需求章节）、[03](03-architecture.md)（AI 架构与网关）、[04](04-database-design.md)（oa_ai_* 表摘要）、[05](05-api-spec.md)（/api/v1/ai 摘要）、[07](07-roadmap.md)（M1–M5 排期）、[10](10-module-plan-index.md)（模块 10 行）。实施期完成后在 [09](09-foundation.md) 补实施记录。端侧 H5 移动 AI（§10）随 [23](23-module-plan-h5.md) H6 实施记录一并回写 [09](09-foundation.md) §21。

## 10. 端侧 H5 移动 AI 开发计划（agentoa-uniapp，[23](23-module-plan-h5.md) H6 承接）

**目标**：移动端获得 AI 对话与知识问答入口，复用本模块 M2/M3 后端能力（`/api/v1/ai/*`，限额/用量/日志/脱敏全走 `LlmGateway` 统一口径），**不新增后端模块、不重复实现网关**。H5 为主交付形态，微信小程序同步构建（`dist/build/{h5,mp-weixin}`）。阶段编号沿 [23](23-module-plan-h5.md) **H6**（H5-H6-01…04），本节为其 AI 子项的需求细化、端侧工程约定与实施记录；M1 管理端、M4 Copilot、M5 Agent/MCP 移动端暂不纳面（见 §10.6 偏差登记）。

### 10.1 功能需求（端侧 AI-H5，编号与 [23] H5-H6-xx 对应）

| 编号 | 需求 | 说明 |
|---|---|---|
| AI-H5-01（H5-H6-01） | AI 对话 | 会话列表面板（切换/删除/新建）+ 流式打字机渲染；**停止生成**（本地 `abort` + `POST /chat/messages/{id}/stop`，消息置 `stopped`）；**重新生成**（保留旧版本，产出新 assistant 消息）；失败消息级提示（错误码可读文案）+ 单条重生成入口 |
| AI-H5-02（H5-H6-02） | 知识问答 | 提问 + 流式回答 + 引用列表（序号/标题/章节/片段），点击引用跳移动端文档预览（`document` 型按 `docId` 跳 `pages/knowledge/preview`，`link` 兜底）；历史问答（`GET /qa/history`）；引用命中以服务端可见范围过滤为准（§5.1 AI-M3-07），跳转不越权 |
| AI-H5-03（H5-H6-03） | 多模态输入 | 聊天图片 ≤5 张、单张 ≤10 MiB、jpg/png/webp，端侧前置预检与服务端 `AiAttachmentRules` 同口径；经 `POST /chat/attachments` 上传取 `fileId`，随消息 `attachmentIds` 参与推理（vision 模型；非 vision 模型 400 `MODEL_CAPABILITY_MISMATCH` 信封提示） |
| AI-H5-04（H5-H6-01） | 流式双通道 | SSE 事件协议与 PC `agentoa-frontend/src/api/ai/stream.ts` 一致（`meta/delta/usage/done/error`）；H5 用 `fetch` + ReadableStream；小程序无 fetch，用 `uni.request({ enableChunked: true })` + `onChunkReceived` 分段拼帧（[23] §6.1 未决项 1 定案：TCP 有序、本地按 `\n\n` 切帧，无重排/丢段风险，不采用轮询降级） |
| AI-H5-05（H5-H6-03） | 治理与错误 | 限额超限 429（含流式中途中断）、能力不匹配 400、上游故障等错误信封转可读提示；用量/限额/审计全在服务端 `LlmGateway`，端侧零治理逻辑、不落 Key |
| AI-H5-06（H5-H6-04） | 入口与权限 | 工作台九宫格 + `mine` 菜单「AI 助手/知识问答」，按 `ai:chat:use`/`ai:qa:use` 显隐（`utils/auth.hasPermission` 与后端权限串对齐） |
| AI-H5-07 | 安全呈现 | 回答与引用按**纯文本**渲染（无 `v-html`、无 markdown 链接执行面）；常驻标注「AI 生成内容仅供参考」；会话内容与 Key 不落 storage（仅 token 会话，[23] §5 口径） |

### 10.2 API 对齐（全部复用 §4.3/§5.3 契约，仅附件上传为 M2 既有端点的移动端接入）

| 移动端封装（`src/api/ai.js` 等） | 后端端点 | 说明 |
|---|---|---|
| `listConversations` / `createConversation` / `updateConversation` / `deleteConversation` / `listMessages` | `GET/POST /chat/conversations`、`GET/PUT/DELETE /chat/conversations/{id}`、`GET /chat/conversations/{id}/messages` | 会话与消息；创建会话自动带 `Idempotency-Key` |
| `chatCompletions` | `POST /chat/completions`（SSE） | 流式对话（`conversationId/content/attachmentIds`，`meta` 事件回传 conversationId/messageId） |
| `stopMessage` | `POST /chat/messages/{id}/stop` | 停止生成（服务端取消上游、消息置 `stopped`） |
| `regenerate` | `POST /chat/messages/{id}/regenerate`（SSE） | 重新生成（保留旧版本） |
| `askQa` / `qaHistory` | `POST /qa/ask`（SSE） / `GET /qa/history` | 知识问答（`done` 事件带引用列表）与历史 |
| `uploadAiAttachment`（`src/api/file.js`） | `POST /chat/attachments`（multipart，`AiChatController`） | 聊天图片附件，服务端三重校验（§4.1 AI-M2-04） |
| `listChatTemplates` / `listModels` | `GET /chat/templates` / `GET /models/enabled` | 已接线会话内模型/模板切换 picker（§10.5 补强实施记录，口径同 §4.1 AI-M2-06） |

### 10.3 工程落点（agentoa-uniapp）

| 落点 | 说明 |
|---|---|
| `src/utils/stream.js` | SSE 双通道统一收口：`event:`/`data:` 帧解析、`{ done, abort }` 取消、`Authorization` + `X-Request-Id` 头；H5 fetch / 小程序 chunked 平台分支 |
| `src/api/ai.js` | 会话/消息/模板/模型/停止/重生成/问答/历史 + `chatCompletions`/`regenerate`/`askQa` 三个流式封装 |
| `src/api/file.js` | `uploadAiAttachment` → `/api/v1/ai/chat/attachments` |
| `src/pages/ai/chat.vue` | 对话页：会话面板、打字机、停止/重生成、图片附件（前置预检 + 缩略图）、错误码消息级提示、免责声明 |
| `src/pages/ai/qa.vue` | 问答页：流式回答、引用列表与跳转、历史问答、停止生成、免责声明 |
| `src/pages.json` | `pages/ai/chat`（AI 助手）、`pages/ai/qa`（知识问答）注册 |
| `src/pages/workbench/index.vue`、`src/pages/mine/index.vue` | 工作台九宫格与 mine 菜单入口，按 `ai:chat:use`/`ai:qa:use` 显隐 |

### 10.4 测试与退出条件

测试项：会话/消息越权（他人会话 404，服务端过滤为准）；停止/重生成状态机（`stopped`/`error`/`done`）；图片前置预检与服务端 400 口径一致（张数/大小/扩展名，魔数仍归服务端三重校验）；SSE 事件序列（`meta → delta → done|error`）H5 与小程序双通道各自跑通；限额 429（含流式中途中断）与能力不匹配 400 可读提示；引用跳转不越权；退出登录无会话内容残留 storage。退出条件（同 [23] §4.6）：H5 真机流式对话可用、引用跳转不越权；小程序端至少可用 chunked 分段通道；限额/错误提示口径与 PC 一致；移动端不落 Key/敏感内容。

### 10.5 实施记录（2026-10-08，随 [23](23-module-plan-h5.md) §7.4 H6 交付）

| 编号 | 交付 | 落点 |
|---|---|---|
| AI-H5-01 | AI 对话 | `pages/ai/chat.vue`：会话面板（切换/删除）、流式打字机、停止生成（`abort` + `POST /chat/messages/{id}/stop`，`meta` 事件取 `messageId`）、重新生成（保留旧版本）、失败消息级提示（错误码 + 重生成入口） |
| AI-H5-02 | 知识问答 | `pages/ai/qa.vue`：流式回答 + 引用列表（`index/title/heading/snippet`），点击跳 `pages/knowledge/preview?id=`（docId）或 `link`；`GET /qa/history` 历史问答 |
| AI-H5-03 | 多模态 | 图片 ≤5 张 / ≤10 MiB / jpg/png/webp 前置预检（`IMAGE_RE/MAX_IMAGES/MAX_IMAGE_BYTES`，与 `AiAttachmentRules` 同口径）→ `uploadAiAttachment` → `attachmentIds` 参与推理 |
| AI-H5-04 | 流式双通道 | `utils/stream.js`：H5 `fetch`+ReadableStream、小程序 `enableChunked`+`onChunkReceived` 拼帧；事件 `meta/delta/usage/done/error` 与 PC `stream.ts` 对齐；`{done, abort}` 支持停止生成 |
| AI-H5-05 | 治理与错误 | 非 2xx 信封（`msg`）与流内 `error` 事件转可读提示；限额/能力不匹配/上游故障口径与 PC 一致，端侧零治理逻辑 |
| AI-H5-06 | 入口与权限 | 工作台九宫格 + mine 菜单「AI 助手/知识问答」，`ai:chat:use`/`ai:qa:use` 显隐 |
| AI-H5-07 | 安全呈现 | 回答/引用纯文本渲染（`user-select` 文本节点，无 `v-html`）；常驻「AI 生成内容仅供参考」 |

验证证据（[23](23-module-plan-h5.md) §7.5）：`agentoa-uniapp` 三门禁全绿（`lint:eslint` / `typecheck`（vue-tsc）/ `build:h5` + `build:mp-weixin`）；`scripts/smoke.mjs` 回归通过（覆盖 `/ai/chat/*`、`/ai/qa/*`、`/ai/chat/attachments` 上传校验、SSE 事件序列、限额 429 等 AI M1–M5 契约，全量口径见 §9.2）；后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（agentoa-ai 80 例）；`scripts/h5-smoke.mjs` PASS 10 项（AI 页暂未纳入，见 §10.6-4）。

**补强实施记录（2026-10-08 第二批，闭环 §10.6-1/§10.6-4）**

| 编号 | 交付 | 落点 |
|---|---|---|
| AI-H5-01/03 补强 | 模型/提示词模板切换接线（§10.6-1） | `pages/ai/chat.vue` 工具条双 picker（模型/模板）接 `listModels`/`listChatTemplates`：默认选 `isDefault` 模型；会话内切换经 `updateConversation` 持久化绑定，下一次发送携带 `modelId`/`promptTemplateId`（口径同 §4.1 AI-M2-06：历史消息不重算、切换后新消息用新模型）；`selectSession` 沿用 PC 自愈口径——会话绑定模型已删除/停用时保留当前选择，发送时自愈绑定。顺带修复两处状态机缺陷：失败/停止消息保留服务端 `messageId`（原用本地假 id，「重新生成」请求 404 不可用），用户「停止生成」消息置 `stopped` + 重生成入口（原误落网络异常 error 气泡） |
| AI-H5-04 补强 | `h5-smoke.mjs` AI 段（§10.6-4） | 新增 12 项断言：AI 对话页渲染与默认模型选择器、流式产出（meta→delta→done）与默认模型绑定、会话内模型切换、提示词模板切换、停止生成状态机（stopped）、失败消息级提示（`LLM_UPSTREAM_ERROR`）、单条重新生成（保留旧版本）、知识问答页渲染、流式回答与引用列表、引用跳转文档预览、历史问答渲染。**mock 上游**：`h5-smoke.mjs` 进程内 OpenAI 兼容服务（`/v1/chat/completions` 流式/非流式 + `/v1/embeddings`，`MOCK_SLOW` 慢速流供停止断言、`MOCK_ERROR` 首次 500 供失败重生成），后端经 `host.docker.internal` 访问（`deploy/compose.smoke.yml` backend 增 `extra_hosts: host.docker.internal:host-gateway`）；模型 `modelKey`/别名带时间戳防跨次运行同名残留干扰路由。断言总数 10 → **22**（两次连跑全绿） |
| 契约修复 | mock 上游暴露的两处后端缺陷 | SSE nginx 缓冲吞 delta（打字机失效）、多知识域异构向量模型错分——见 §9.2「端侧 H5 冒烟补强暴露项」 |

验证证据（2026-10-08 第二批）：`scripts/h5-smoke.mjs` **PASS 22** 项（连跑两次）；后端 `mvn -pl ruoyi-admin -am verify` BUILD SUCCESS（agentoa-ai **81** 例，含 `AiQaRagH2Test#queryEmbeddingRunsPerKbEmbeddingModel`）；`smoke.mjs` **500** 项 PASS；`browser-smoke.mjs` PASS（axe 基线 4/11/12/10/9 不变）；`agentoa-uniapp` 三门禁全绿。

### 10.6 偏差登记与未尽事项

1. **模型/提示词模板切换未接线**：**已接线（2026-10-08 第二批）**——`pages/ai/chat.vue` 工具条模型/模板 picker（详见 §10.5 补强实施记录），会话内切换口径同 §4.1 AI-M2-06。已知限制：模板仅支持**换绑**不支持**清除**（服务端 `updateConversation` 对空 `promptTemplateId` 为无操作，与 PC clearable 选择器同口径），新会话默认无模板。
2. **Agent 模式（M5 `step` 轨迹）与业务 Copilot（M4）移动端入口暂不纳入**：移动侧聚焦 M2/M3 面向全员的对话/问答；Copilot 场景抽屉与 Agent 轨迹回放依赖 PC 复杂交互，维持 PC 形态（`views/ai/{copilot,agent}` + `AiCopilotDrawer`），如需移动 Copilot 另行立项。
3. **`usage` 事件端侧暂不消费**：SSE `usage` 事件按协议透传但移动端不做用量展示（用量看板在 PC 管理端 `views/ai/usage`）；限额判定始终在服务端，不受影响。
4. **`h5-smoke.mjs` 未覆盖 AI 页**：**已补强（2026-10-08 第二批）**——AI 对话/问答渲染与流式 12 项断言（mock 上游，见 §10.5 补强实施记录），断言总数 10 → 22，§10.4 退出条件的冒烟口径同步更新为 22 项。
5. **小程序流式真机验证归 H7**：`enableChunked` 通道代码路径就绪，真机/微信开发者工具验证随 [23](23-module-plan-h5.md) H7、[24](24-release-acceptance-checklist.md) 出记录；`onChunkReceived` 不可用时上报 `STREAM_UNSUPPORTED` 并中止（不静默降级）。
6. **渲染口径从严**：移动端 AI 输出按纯文本渲染，不引入 markdown 白名单渲染（PC 侧为 `utils/markdown.ts` + `safeUrl` 协议白名单），无 XSS 执行面；如需富文本展示须先完成安全审定。
7. **QA 会话与对话会话隔离**：知识问答历史走 `GET /qa/history`（`oa_ai_conversation.scene` 区分），移动端不与 AI 对话会话列表混用，与服务端 M3 口径一致。
