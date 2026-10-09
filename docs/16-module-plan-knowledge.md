# 模块 6：知识库开发计划

## 目标与边界

实现空间级知识库和私有文件柜。P0 包括空间、成员角色、Markdown 文档、版本、发布/归档、文件柜、搜索和回收站；文件夹覆盖权限、富文本双编辑、在线 Office、全文高级搜索和协作编辑列为 P1/P2。

## 开发顺序

1. V9 迁移：`oa_knowledge_space`、`oa_knowledge_member`、`oa_document`、`oa_document_version`、`oa_document_acl`、`oa_document_file`、`oa_document_revision`。
2. 建立空间角色 `OWNER/EDITOR/COMMENTER/VIEWER` 和继承规则；默认无权限，管理员也需审计授权。
3. 实现文档版本提交：客户端携带 `baseVersion`，服务端乐观锁冲突返回 409；历史版本不可被覆盖。
4. 文件上传复用 `sys_file`，新增业务绑定和下载授权；删除进入回收站，保留恢复期限和审计。
5. 实现标题/正文/标签搜索，先使用 MySQL 可验证查询；索引升级单独评估，不承诺搜索引擎。
6. 前端空间列表、成员管理、Markdown 编辑器、版本比较、文件柜、搜索和回收站。

## API 与规则

`GET/POST/PUT /api/v1/knowledge/spaces`、`/spaces/{id}/members`、`/documents`、`/documents/{id}/versions`、`/documents/{id}/restore`、`/documents/search`、`POST /documents/{id}/files`。任何列表、搜索、历史、下载都必须以空间/文档授权过滤，不能先查全量再由前端隐藏。

## 测试与退出条件

测试空间越权、成员移除后旧链接、文档并发编辑、版本历史不可变、文件回收恢复、删除后搜索结果、下载审计和大文件限制。退出条件是两个不同角色在同一文档上的可见、可编辑、可下载结果均符合矩阵。

## 实施记录（2026-10-04，P0 完成）

P0 已完成并通过验证。接口以 [05](05-api-spec.md) 第 8 节为准（8.5 实现口径补充），与本开发包的差异如下（同步 [09](09-foundation.md) 第 13 节）：

- 路径/权限口径：采用本包 `/api/v1/knowledge` 前缀（05 草案 8.1–8.4 的 `/api/v1/kb` 不再使用）：`GET/POST /spaces`、`GET/PUT/DELETE /spaces/{id}`、`GET/POST /spaces/{id}/members`、`PUT/DELETE /spaces/{id}/members/{userId}`、`GET/POST /documents`、`GET/PUT/DELETE /documents/{id}`、`GET /documents/search`、`GET /documents/{id}/versions`、`GET /documents/{id}/versions/{version}`、`PUT /documents/{id}/rollback/{version}`、`PUT/GET /documents/{id}/draft`、`POST /documents/{id}/restore`、`PUT /documents/{id}/publish`、`PUT /documents/{id}/archive`、`GET/POST /files`、`POST /documents/{id}/files`、`GET /files/{id}/download|preview`、`DELETE /files/{id}`、`POST /files/{id}/restore`。权限串 `kn:space:add|edit|remove`、`kn:member:add|edit|remove`、`kn:doc:add|edit|remove|restore`、`kn:file:upload|remove`；读接口登录即可，但列表/搜索/历史/下载全部在服务端按授权过滤。
- V9 迁移：`oa_knowledge_space`、`oa_knowledge_member`、`oa_document`、`oa_document_version`、`oa_document_revision`、`oa_document_acl`、`oa_document_file`。与 docs/04 `kb_*` 草案的差异：目录并入 `oa_document.parent_id` 文档树（无独立目录表）；个人草稿独立为 `oa_document_revision`（document_id+user_id 唯一）；回收站由 `deleted_at/deleted_by` 表达，`status` 仅表示 1草稿 2已发布 3已归档；文件柜 `oa_document_file` 关联 `sys_file` 并带下载审计与回收站字段。`kn_space_type`/`kn_space_role`/`kn_doc_status` 字典、`kn:*` 菜单（2500–2521）、角色授权（HR/主管/员工全按钮，财务/出纳/总监只读菜单）。空库迁移与已有数据升级各执行一次（空库 V1–V9 全量、存量库单步 V9）验证通过。
- 角色与授权：空间角色 `OWNER/EDITOR/COMMENTER/VIEWER` 与文档级 ACL（USER/ROLE 主体）取较高者；默认无权限，管理员也需显式授权（内容访问不做超管旁路）；公开空间全员 VIEWER、私密仅创建人 OWNER、团队仅成员可见。成员授权记录 `granted_by/granted_time` 留审计；移除成员后旧链接立即 404；空间至少保留一名 OWNER。
- 版本与并发：更新必须携带 `baseVersion`，条件更新（`WHERE version = baseVersion`）不匹配返回 409 `VERSION_CONFLICT`；`oa_document_version`（document_id+version 唯一）只追加、历史不可覆盖；回滚生成新版本；个人草稿 30 秒自动保存并记录 `base_version`。
- 发布/归档与回收站：`publish`（仅草稿）、`archive`（归档后拒绝编辑/回滚）；删除进回收站并记录删除人/时间，30 天内可恢复、逾期 409；回收站内容从搜索与默认列表剔除。
- 文件柜：复用 `sys_file` 与私有 S3 桶，`oa_document_file` 负责业务绑定（可关联文档）、下载授权（继承空间/文档角色）、下载审计（`download_count/last_download_by/last_download_time` + `@Log`）与回收站；单文件 ≤ 20 MiB，扩展名、内容魔数与 MIME 三重校验（PDF/PNG/JPEG/TXT/MD）；预览限图片/PDF/文本。
- 搜索：标题/正文纯文本（`content_text`，Markdown 去标记）/标签的 MySQL LIKE 查询 + `<em>` 高亮，按成员空间/公开空间/自建私密空间/文档 ACL 服务端过滤；未引入搜索引擎（升级按容量单独评估）。
- 前端 `views/knowledge/{space,document,files,search,trash}` + `api/knowledge`：空间与成员管理（展示授权人/时间）、Markdown 左右分栏编辑（轻量渲染器 `utils/markdown` + highlight.js common）、版本历史/行级 diff 对比/回滚、文件柜上传下载预览、搜索高亮、回收站双页签恢复。

退出验证：空库迁移与已有数据升级各一次（V1–V9 / 单步 V9）；`agentoa-knowledge` 27 个 H2 测试（权限矩阵与默认拒绝、空间越权、成员移除后旧链接失效、最后 OWNER 保护、版本并发 409、历史不可覆盖、回收站 30 天窗口、删除后搜索不可见、文档 ACL 授权、文件大小/内容校验、下载审计、越权下载 404）；`smoke.mjs` 270 项全部通过（含模块 6 契约：OWNER/EDITOR/VIEWER 在同一文档上的可见/可编辑/可下载矩阵、版本冲突 409、回收恢复、成员移除后 404）；`browser-smoke.mjs` 通过（知识空间/文档/文件柜/搜索/回收站页面渲染）；前端 `build:prod` + `vue-tsc` 通过。本模块退出条件（两角色在同一文档上的可见/可编辑/可下载符合矩阵）由 smoke 与 H2 测试覆盖。

P0 未包含（P1/P2）：文件夹覆盖权限（P1，目录以 `parent_id` 文档树实现基础多级结构，拖拽排序未做）、评论/点赞/收藏与标签增强（P1）、富文本双编辑（P1）、在线 Office 预览/协作（P2）、协作编辑（P2）、全文高级搜索/ES（按容量评估）、大文件分片上传（P1）。后续按 [17](17-module-plan-collaboration.md) 顺序推进。

## 变更记录

### 2026-10-08：文档批量详情接口（docs/22 FE-F4-02 后端变更）

- 新增 `GET /api/v1/knowledge/documents/batch?ids=1,2,3` → `List<DocumentVo>`：`KnowledgeDocumentService.details(ids, actorUserId)`，`selectBatchIds`（上限 200、去重去空）+ 服务端授权过滤（`documentRole` + `canView`），**无权限/已删除文档静默丢弃**（不报错、不泄露，与收藏页原逐条拉取的跳过语义一致）；用于收藏列表等批量展示场景（原前端逐 id 串行 `GET /documents/{id}` N+1）。
- 路由无歧义：`/batch` 字面量优先于 `/{id}` 模板匹配。既有接口契约不变，`GET /documents/{id}` 单条语义（无权限 404/403）不动。
- 回归用例：`KnowledgeDocumentH2Test#batchDetailsFilterByAuthorizationAndDropDeleted`（成员可见集取回、越权静默丢弃、已删除不出现在结果）。
