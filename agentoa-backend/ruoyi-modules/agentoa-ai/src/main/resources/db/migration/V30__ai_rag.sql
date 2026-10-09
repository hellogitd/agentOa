-- AgentOA module 10 (M3): knowledge QA (RAG) - knowledge domains, sources and chunks.
-- API contract: docs/21-module-plan-ai.md section 5. Vector storage P0: embedding BLOB + in-app
-- cosine similarity (hot cache); store_type reserved for dedicated vector stores (docs/21 §9.2).
-- Retrieval is permission filtered at query time: the asker only reaches chunks whose source
-- (oa_document / direct file) is within his visible scope.

-- ---------------------------------------------------------------- knowledge domains, sources, chunks

CREATE TABLE oa_ai_kb (
  id                 BIGINT        NOT NULL                   COMMENT '知识域ID',
  tenant_id          VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  name               VARCHAR(128)  NOT NULL                   COMMENT '知识域名称',
  description        VARCHAR(500)  DEFAULT NULL               COMMENT '描述',
  visibility         VARCHAR(16)   NOT NULL DEFAULT 'private' COMMENT '可见性（ai_kb_visibility：private/members/all）',
  member_scope       VARCHAR(2000) DEFAULT NULL               COMMENT '成员授权（JSON：账号ID 数组）',
  embedding_model_id BIGINT        DEFAULT NULL               COMMENT '向量模型ID（capability 需含 embedding）',
  status             VARCHAR(16)   NOT NULL DEFAULT 'active'  COMMENT '状态（active/disabled）',
  create_dept        BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_kb_status (status, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识域（RAG 检索单元）';

CREATE TABLE oa_ai_kb_source (
  id            BIGINT        NOT NULL                   COMMENT '数据源ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  kb_id         BIGINT        NOT NULL                   COMMENT '所属知识域ID',
  source_type   VARCHAR(16)   NOT NULL                   COMMENT '来源类型（document/file）',
  doc_id        BIGINT        DEFAULT NULL               COMMENT '知识库文档ID（oa_document）',
  file_id       BIGINT        DEFAULT NULL               COMMENT '直传文件ID（sys_file）',
  title         VARCHAR(255)  DEFAULT NULL               COMMENT '标题（冗余展示）',
  chunk_count   INT           NOT NULL DEFAULT 0         COMMENT '分块数量',
  index_status  VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT '索引状态（ai_kb_index_status）',
  error_msg     VARCHAR(500)  DEFAULT NULL               COMMENT '索引失败原因',
  indexed_at    DATETIME      DEFAULT NULL               COMMENT '最近索引完成时间',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_kb_source_kb (kb_id, index_status),
  KEY idx_ai_kb_source_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识域数据源（知识文档/直传文件）';

CREATE TABLE oa_ai_kb_chunk (
  id           BIGINT        NOT NULL                   COMMENT '分块ID',
  tenant_id    VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  kb_id        BIGINT        NOT NULL                   COMMENT '知识域ID',
  source_id    BIGINT        NOT NULL                   COMMENT '数据源ID',
  seq          INT           NOT NULL DEFAULT 0         COMMENT '块序号（0 起）',
  heading      VARCHAR(255)  DEFAULT NULL               COMMENT '所属标题/章节',
  content      TEXT          NOT NULL                   COMMENT '块正文',
  token_count  INT           NOT NULL DEFAULT 0         COMMENT '块 token 估算',
  embedding    BLOB          DEFAULT NULL               COMMENT '向量（float32 小端数组）',
  store_type   VARCHAR(16)   NOT NULL DEFAULT 'blob'    COMMENT '向量存储类型（blob，预留扩展）',
  create_dept  BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by    BIGINT        DEFAULT NULL               COMMENT '创建者账号ID',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time  DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark       VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_ai_kb_chunk_source (source_id, seq),
  KEY idx_ai_kb_chunk_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识分块（embedding BLOB + 应用内余弦相似度）';

-- ---------------------------------------------------------------- QA 会话场景与消息引用

-- QA 复用对话/消息表（docs/21 §5.3 conversation_id 可选）；scene 区分入口，引用随消息留存。
ALTER TABLE oa_ai_conversation ADD COLUMN scene VARCHAR(16) NOT NULL DEFAULT 'chat' COMMENT '场景（chat/qa）';
ALTER TABLE oa_ai_message ADD COLUMN citations TEXT DEFAULT NULL COMMENT '知识问答引用（JSON 数组）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(146, '000000', 'AI 索引状态', 'ai_kb_index_status', 100, 1, sysdate(), null, null, '知识域数据源索引状态');
INSERT INTO sys_dict_data VALUES(1460, '000000', 1,  '待索引', 'pending',  'ai_kb_index_status', '', 'info',    'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1461, '000000', 2,  '索引中', 'indexing', 'ai_kb_index_status', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1462, '000000', 3,  '已就绪', 'ready',    'ai_kb_index_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1463, '000000', 4,  '失败',   'failed',   'ai_kb_index_status', '', 'danger',  'N', 100, 1, sysdate(), null, null, '可重新索引');
INSERT INTO sys_dict_type VALUES(147, '000000', 'AI 知识域可见性', 'ai_kb_visibility', 100, 1, sysdate(), null, null, '知识域可见范围');
INSERT INTO sys_dict_data VALUES(1470, '000000', 1,  '私有',   'private', 'ai_kb_visibility', '', 'info',    'Y', 100, 1, sysdate(), null, null, '仅创建者与显式成员');
INSERT INTO sys_dict_data VALUES(1471, '000000', 2,  '指定人', 'members', 'ai_kb_visibility', '', 'primary', 'N', 100, 1, sysdate(), null, null, '创建者与成员可见');
INSERT INTO sys_dict_data VALUES(1472, '000000', 3,  '全员',   'all',     'ai_kb_visibility', '', 'success', 'N', 100, 1, sysdate(), null, null, '全员可检索');

-- ---------------------------------------------------------------- menus and buttons (docs/21 §2.4)

INSERT INTO sys_menu VALUES(3002, '知识问答', 3000, 2, 'qa', 'ai/qa/index', '', 1, 0, 'C', '0', '0', '', 'search', 100, 1, sysdate(), null, null, '知识库问答（RAG，带引用溯源）');
INSERT INTO sys_menu VALUES(3011, '问答使用', 3002, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:qa:use', '#', 100, 1, sysdate(), null, null, '使用知识问答');
INSERT INTO sys_menu VALUES(3025, '知识库管理', 3020, 5, 'kb', 'ai/kb/index', '', 1, 0, 'C', '0', '0', '', 'documentation', 100, 1, sysdate(), null, null, '知识域/数据源/索引状态/检索测试');
INSERT INTO sys_menu VALUES(3064, '知识域查询', 3025, 1, '', '', '', 1, 0, 'F', '0', '0', 'ai:kb:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3065, '知识域新增', 3025, 2, '', '', '', 1, 0, 'F', '0', '0', 'ai:kb:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3066, '知识域修改', 3025, 3, '', '', '', 1, 0, 'F', '0', '0', 'ai:kb:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3067, '知识域删除', 3025, 4, '', '', '', 1, 0, 'F', '0', '0', 'ai:kb:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(3068, '索引与检索测试', 3025, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:kb:index', '#', 100, 1, sysdate(), null, null, '数据源索引/检索测试');

-- 全员可知识问答（docs/21 §5）；知识库管理默认仅管理员（同 AI 管理口径，docs/21 §2.3）。
INSERT INTO sys_role_menu VALUES(20, 3002);
INSERT INTO sys_role_menu VALUES(20, 3011);
INSERT INTO sys_role_menu VALUES(21, 3002);
INSERT INTO sys_role_menu VALUES(21, 3011);
INSERT INTO sys_role_menu VALUES(22, 3002);
INSERT INTO sys_role_menu VALUES(22, 3011);
INSERT INTO sys_role_menu VALUES(23, 3002);
INSERT INTO sys_role_menu VALUES(23, 3011);
INSERT INTO sys_role_menu VALUES(24, 3002);
INSERT INTO sys_role_menu VALUES(24, 3011);
INSERT INTO sys_role_menu VALUES(25, 3002);
INSERT INTO sys_role_menu VALUES(25, 3011);
INSERT INTO sys_role_menu VALUES(26, 3002);
INSERT INTO sys_role_menu VALUES(26, 3011);
INSERT INTO sys_role_menu VALUES(27, 3002);
INSERT INTO sys_role_menu VALUES(27, 3011);
INSERT INTO sys_role_menu VALUES(26, 3025);
INSERT INTO sys_role_menu VALUES(26, 3064);
INSERT INTO sys_role_menu VALUES(26, 3065);
INSERT INTO sys_role_menu VALUES(26, 3066);
INSERT INTO sys_role_menu VALUES(26, 3067);
INSERT INTO sys_role_menu VALUES(26, 3068);
