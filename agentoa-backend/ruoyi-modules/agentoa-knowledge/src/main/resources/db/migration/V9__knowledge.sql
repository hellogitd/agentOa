-- AgentOA module 6: knowledge spaces, members, documents, versions, per-document ACL,
-- file cabinet bindings, personal drafts and recycle bin bookkeeping.
-- API contract: docs/16-module-plan-knowledge.md (paths /api/v1/knowledge/**, docs/05 section 8 aligned).
-- File payloads reuse V2 sys_file (private S3 bucket) with business binding and download authorization here.

-- ---------------------------------------------------------------- spaces and members

CREATE TABLE oa_knowledge_space (
  id            BIGINT        NOT NULL                   COMMENT '空间ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  name          VARCHAR(128)  NOT NULL                   COMMENT '空间名称',
  icon          VARCHAR(64)   DEFAULT NULL               COMMENT '空间图标',
  description   VARCHAR(500)  DEFAULT NULL               COMMENT '空间简介',
  space_type    TINYINT       NOT NULL DEFAULT 1         COMMENT '空间类型（1公开 2私密 3团队）',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_knowledge_space_type (space_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间（默认无权限：可见性来自成员授权或空间类型）';

CREATE TABLE oa_knowledge_member (
  id            BIGINT        NOT NULL                   COMMENT '成员记录ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  space_id      BIGINT        NOT NULL                   COMMENT '空间ID',
  user_id       BIGINT        NOT NULL                   COMMENT '账号ID',
  role          VARCHAR(16)   NOT NULL                   COMMENT '空间角色（OWNER/EDITOR/COMMENTER/VIEWER）',
  granted_by    BIGINT        DEFAULT NULL               COMMENT '授权人账号ID（审计）',
  granted_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间（审计）',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_member (space_id, user_id),
  KEY idx_knowledge_member_user (user_id, space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间成员（授权审计：记录授权人与授权时间）';

-- ---------------------------------------------------------------- documents

CREATE TABLE oa_document (
  id               BIGINT        NOT NULL                   COMMENT '文档ID',
  tenant_id        VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  space_id         BIGINT        NOT NULL                   COMMENT '所属空间ID',
  parent_id        BIGINT        NOT NULL DEFAULT 0         COMMENT '父文档ID（0为根，形成目录树）',
  title            VARCHAR(255)  NOT NULL                   COMMENT '标题',
  content          LONGTEXT      DEFAULT NULL               COMMENT '正文（Markdown）',
  content_text     LONGTEXT      DEFAULT NULL               COMMENT '纯文本（搜索用）',
  doc_type         VARCHAR(32)   NOT NULL DEFAULT 'markdown' COMMENT '类型（markdown/rich）',
  tags             VARCHAR(255)  DEFAULT NULL               COMMENT '标签（逗号分隔）',
  version          INT           NOT NULL DEFAULT 1         COMMENT '当前版本号（乐观锁基线）',
  status           TINYINT       NOT NULL DEFAULT 1         COMMENT '状态（1草稿 2已发布 3已归档）',
  is_top           TINYINT       NOT NULL DEFAULT 0         COMMENT '是否置顶',
  view_count       INT           NOT NULL DEFAULT 0         COMMENT '浏览次数',
  deleted_at       DATETIME      DEFAULT NULL               COMMENT '回收站起算时间（NULL未删除）',
  deleted_by       BIGINT        DEFAULT NULL               COMMENT '删除操作账号ID（审计）',
  last_edit_by     BIGINT        DEFAULT NULL               COMMENT '最后编辑账号ID',
  last_edit_time   DATETIME      DEFAULT NULL               COMMENT '最后编辑时间',
  create_dept      BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by        BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time      DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark           VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_document_space (space_id, deleted_at),
  KEY idx_document_parent (parent_id),
  KEY idx_document_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档（软删除进回收站，30天内可恢复）';

CREATE TABLE oa_document_version (
  id              BIGINT        NOT NULL                   COMMENT '版本记录ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id     BIGINT        NOT NULL                   COMMENT '文档ID',
  version         INT           NOT NULL                   COMMENT '版本号',
  title           VARCHAR(255)  DEFAULT NULL               COMMENT '标题快照',
  content         LONGTEXT      DEFAULT NULL               COMMENT '正文快照',
  change_summary  VARCHAR(255)  DEFAULT NULL               COMMENT '变更摘要',
  create_by       BIGINT        DEFAULT NULL               COMMENT '提交人账号ID',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_version (document_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本历史（只追加，不可覆盖）';

CREATE TABLE oa_document_revision (
  id              BIGINT        NOT NULL                   COMMENT '草稿记录ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id     BIGINT        NOT NULL                   COMMENT '文档ID',
  user_id         BIGINT        NOT NULL                   COMMENT '草稿所属账号ID',
  base_version    INT           NOT NULL                   COMMENT '编辑时基线版本（并发冲突检测）',
  title           VARCHAR(255)  DEFAULT NULL               COMMENT '草稿标题',
  content         LONGTEXT      DEFAULT NULL               COMMENT '草稿正文',
  update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '保存时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_revision (document_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档个人草稿（自动保存，携带 baseVersion）';

CREATE TABLE oa_document_acl (
  id              BIGINT        NOT NULL                   COMMENT '授权记录ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id     BIGINT        NOT NULL                   COMMENT '文档ID',
  subject_type    VARCHAR(8)    NOT NULL                   COMMENT '主体类型（USER/ROLE）',
  subject_id      BIGINT        NOT NULL                   COMMENT '主体ID（账号ID/角色ID）',
  role            VARCHAR(16)   NOT NULL                   COMMENT '授权角色（EDITOR/COMMENTER/VIEWER）',
  granted_by      BIGINT        DEFAULT NULL               COMMENT '授权人账号ID（审计）',
  granted_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间（审计）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_acl (document_id, subject_type, subject_id),
  KEY idx_document_acl_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档级授权（与空间角色取较高者，默认无权限）';

-- ---------------------------------------------------------------- file cabinet

CREATE TABLE oa_document_file (
  id              BIGINT        NOT NULL                   COMMENT '文件柜记录ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  space_id        BIGINT        NOT NULL                   COMMENT '所属空间ID',
  document_id     BIGINT        DEFAULT NULL               COMMENT '关联文档ID（NULL为文件柜独立文件）',
  file_id         BIGINT        NOT NULL                   COMMENT 'sys_file 元数据ID（私有桶）',
  file_name       VARCHAR(255)  NOT NULL                   COMMENT '文件名',
  file_ext        VARCHAR(32)   DEFAULT NULL               COMMENT '扩展名',
  file_size       BIGINT        DEFAULT NULL               COMMENT '大小（字节）',
  content_type    VARCHAR(128)  DEFAULT NULL               COMMENT 'MIME 类型',
  download_count  INT           NOT NULL DEFAULT 0         COMMENT '下载次数',
  deleted_at      DATETIME      DEFAULT NULL               COMMENT '回收站起算时间（NULL未删除）',
  deleted_by      BIGINT        DEFAULT NULL               COMMENT '删除操作账号ID（审计）',
  last_download_by   BIGINT     DEFAULT NULL               COMMENT '最后下载账号ID（审计）',
  last_download_time DATETIME   DEFAULT NULL               COMMENT '最后下载时间（审计）',
  create_dept     BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by       BIGINT        DEFAULT NULL               COMMENT '上传者账号ID',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_document_file_space (space_id, deleted_at),
  KEY idx_document_file_doc (document_id),
  KEY idx_document_file_name (file_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文件柜（复用 sys_file，业务绑定 + 下载授权 + 回收站）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(80, '000000', '知识空间类型', 'kn_space_type', 100, 1, sysdate(), null, null, '知识空间可见范围');
INSERT INTO sys_dict_data VALUES(800, '000000', 1, '公开', '1', 'kn_space_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '全员可见');
INSERT INTO sys_dict_data VALUES(801, '000000', 2, '私密', '2', 'kn_space_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '仅创建人');
INSERT INTO sys_dict_data VALUES(802, '000000', 3, '团队', '3', 'kn_space_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '指定成员可见');
INSERT INTO sys_dict_type VALUES(81, '000000', '知识空间角色', 'kn_space_role', 100, 1, sysdate(), null, null, '空间成员角色');
INSERT INTO sys_dict_data VALUES(810, '000000', 1, '拥有者', 'OWNER', 'kn_space_role', '', 'danger', 'Y', 100, 1, sysdate(), null, null, '管理/编辑/下载');
INSERT INTO sys_dict_data VALUES(811, '000000', 2, '编辑者', 'EDITOR', 'kn_space_role', '', 'primary', 'N', 100, 1, sysdate(), null, null, '编辑/下载');
INSERT INTO sys_dict_data VALUES(812, '000000', 3, '评论者', 'COMMENTER', 'kn_space_role', '', 'warning', 'N', 100, 1, sysdate(), null, null, '查看/下载/评论（评论为P1）');
INSERT INTO sys_dict_data VALUES(813, '000000', 4, '查看者', 'VIEWER', 'kn_space_role', '', 'info', 'N', 100, 1, sysdate(), null, null, '查看/下载');
INSERT INTO sys_dict_type VALUES(82, '000000', '知识文档状态', 'kn_doc_status', 100, 1, sysdate(), null, null, '文档生命周期');
INSERT INTO sys_dict_data VALUES(820, '000000', 1, '草稿', '1', 'kn_doc_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(821, '000000', 2, '已发布', '2', 'kn_doc_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(822, '000000', 3, '已归档', '3', 'kn_doc_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- menus and buttons (docs/16 API scope)

INSERT INTO sys_menu VALUES(2500, '知识库', 0, 11, 'knowledge', null, '', 1, 0, 'M', '0', '0', '', 'education', 100, 1, sysdate(), null, null, '知识库目录');
INSERT INTO sys_menu VALUES(2501, '知识空间', 2500, 1, 'space', 'knowledge/space/index', '', 1, 0, 'C', '0', '0', '', 'tree-table', 100, 1, sysdate(), null, null, '空间与成员管理');
INSERT INTO sys_menu VALUES(2502, '知识文档', 2500, 2, 'document', 'knowledge/document/index', '', 1, 0, 'C', '0', '0', '', 'documentation', 100, 1, sysdate(), null, null, 'Markdown 文档与版本');
INSERT INTO sys_menu VALUES(2503, '文件柜', 2500, 3, 'files', 'knowledge/files/index', '', 1, 0, 'C', '0', '0', '', 'upload', 100, 1, sysdate(), null, null, '上传、下载与预览');
INSERT INTO sys_menu VALUES(2504, '知识搜索', 2500, 4, 'search', 'knowledge/search/index', '', 1, 0, 'C', '0', '0', '', 'search', 100, 1, sysdate(), null, null, '标题/正文/标签搜索');
INSERT INTO sys_menu VALUES(2505, '回收站', 2500, 5, 'trash', 'knowledge/trash/index', '', 1, 0, 'C', '0', '0', '', 'delete', 100, 1, sysdate(), null, null, '30 天内可恢复');
INSERT INTO sys_menu VALUES(2510, '空间新建', 2501, 1, '', '', '', 1, 0, 'F', '0', '0', 'kn:space:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2511, '空间修改', 2501, 2, '', '', '', 1, 0, 'F', '0', '0', 'kn:space:edit', '#', 100, 1, sysdate(), null, null, '仅空间 OWNER');
INSERT INTO sys_menu VALUES(2512, '空间删除', 2501, 3, '', '', '', 1, 0, 'F', '0', '0', 'kn:space:remove', '#', 100, 1, sysdate(), null, null, '仅空间 OWNER');
INSERT INTO sys_menu VALUES(2513, '成员添加', 2501, 4, '', '', '', 1, 0, 'F', '0', '0', 'kn:member:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2514, '成员改角色', 2501, 5, '', '', '', 1, 0, 'F', '0', '0', 'kn:member:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2515, '成员移除', 2501, 6, '', '', '', 1, 0, 'F', '0', '0', 'kn:member:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2516, '文档新建', 2502, 1, '', '', '', 1, 0, 'F', '0', '0', 'kn:doc:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2517, '文档编辑', 2502, 2, '', '', '', 1, 0, 'F', '0', '0', 'kn:doc:edit', '#', 100, 1, sysdate(), null, null, '含版本提交与回滚');
INSERT INTO sys_menu VALUES(2518, '文档删除', 2502, 3, '', '', '', 1, 0, 'F', '0', '0', 'kn:doc:remove', '#', 100, 1, sysdate(), null, null, '删除进回收站');
INSERT INTO sys_menu VALUES(2519, '回收恢复', 2505, 1, '', '', '', 1, 0, 'F', '0', '0', 'kn:doc:restore', '#', 100, 1, sysdate(), null, null, '文档与文件恢复');
INSERT INTO sys_menu VALUES(2520, '文件上传', 2503, 1, '', '', '', 1, 0, 'F', '0', '0', 'kn:file:upload', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2521, '文件删除', 2503, 2, '', '', '', 1, 0, 'F', '0', '0', 'kn:file:remove', '#', 100, 1, sysdate(), null, null, '删除进回收站');

-- Role-menu matrix: every role can read knowledge (visibility still enforced per space/doc);
-- HR, dept manager and employee can contribute; finance/cashier/director get read-only menus.
INSERT INTO sys_role_menu VALUES(20, 2500);
INSERT INTO sys_role_menu VALUES(20, 2501);
INSERT INTO sys_role_menu VALUES(20, 2502);
INSERT INTO sys_role_menu VALUES(20, 2503);
INSERT INTO sys_role_menu VALUES(20, 2504);
INSERT INTO sys_role_menu VALUES(20, 2505);
INSERT INTO sys_role_menu VALUES(20, 2510);
INSERT INTO sys_role_menu VALUES(20, 2511);
INSERT INTO sys_role_menu VALUES(20, 2512);
INSERT INTO sys_role_menu VALUES(20, 2513);
INSERT INTO sys_role_menu VALUES(20, 2514);
INSERT INTO sys_role_menu VALUES(20, 2515);
INSERT INTO sys_role_menu VALUES(20, 2516);
INSERT INTO sys_role_menu VALUES(20, 2517);
INSERT INTO sys_role_menu VALUES(20, 2518);
INSERT INTO sys_role_menu VALUES(20, 2519);
INSERT INTO sys_role_menu VALUES(20, 2520);
INSERT INTO sys_role_menu VALUES(20, 2521);
INSERT INTO sys_role_menu VALUES(21, 2500);
INSERT INTO sys_role_menu VALUES(21, 2501);
INSERT INTO sys_role_menu VALUES(21, 2502);
INSERT INTO sys_role_menu VALUES(21, 2503);
INSERT INTO sys_role_menu VALUES(21, 2504);
INSERT INTO sys_role_menu VALUES(21, 2505);
INSERT INTO sys_role_menu VALUES(21, 2510);
INSERT INTO sys_role_menu VALUES(21, 2511);
INSERT INTO sys_role_menu VALUES(21, 2512);
INSERT INTO sys_role_menu VALUES(21, 2513);
INSERT INTO sys_role_menu VALUES(21, 2514);
INSERT INTO sys_role_menu VALUES(21, 2515);
INSERT INTO sys_role_menu VALUES(21, 2516);
INSERT INTO sys_role_menu VALUES(21, 2517);
INSERT INTO sys_role_menu VALUES(21, 2518);
INSERT INTO sys_role_menu VALUES(21, 2519);
INSERT INTO sys_role_menu VALUES(21, 2520);
INSERT INTO sys_role_menu VALUES(21, 2521);
INSERT INTO sys_role_menu VALUES(22, 2500);
INSERT INTO sys_role_menu VALUES(22, 2501);
INSERT INTO sys_role_menu VALUES(22, 2502);
INSERT INTO sys_role_menu VALUES(22, 2503);
INSERT INTO sys_role_menu VALUES(22, 2504);
INSERT INTO sys_role_menu VALUES(22, 2505);
INSERT INTO sys_role_menu VALUES(22, 2510);
INSERT INTO sys_role_menu VALUES(22, 2511);
INSERT INTO sys_role_menu VALUES(22, 2512);
INSERT INTO sys_role_menu VALUES(22, 2513);
INSERT INTO sys_role_menu VALUES(22, 2514);
INSERT INTO sys_role_menu VALUES(22, 2515);
INSERT INTO sys_role_menu VALUES(22, 2516);
INSERT INTO sys_role_menu VALUES(22, 2517);
INSERT INTO sys_role_menu VALUES(22, 2518);
INSERT INTO sys_role_menu VALUES(22, 2519);
INSERT INTO sys_role_menu VALUES(22, 2520);
INSERT INTO sys_role_menu VALUES(22, 2521);
INSERT INTO sys_role_menu VALUES(23, 2500);
INSERT INTO sys_role_menu VALUES(23, 2501);
INSERT INTO sys_role_menu VALUES(23, 2502);
INSERT INTO sys_role_menu VALUES(23, 2503);
INSERT INTO sys_role_menu VALUES(23, 2504);
INSERT INTO sys_role_menu VALUES(23, 2505);
INSERT INTO sys_role_menu VALUES(24, 2500);
INSERT INTO sys_role_menu VALUES(24, 2501);
INSERT INTO sys_role_menu VALUES(24, 2502);
INSERT INTO sys_role_menu VALUES(24, 2503);
INSERT INTO sys_role_menu VALUES(24, 2504);
INSERT INTO sys_role_menu VALUES(24, 2505);
INSERT INTO sys_role_menu VALUES(25, 2500);
INSERT INTO sys_role_menu VALUES(25, 2501);
INSERT INTO sys_role_menu VALUES(25, 2502);
INSERT INTO sys_role_menu VALUES(25, 2503);
INSERT INTO sys_role_menu VALUES(25, 2504);
INSERT INTO sys_role_menu VALUES(25, 2505);
