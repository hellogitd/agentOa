-- AgentOA knowledge P1: document comments, likes and favorites (KB-06/KB-07).
-- API contract: docs/05-api-spec.md section 8.3. Comment/like/favorite all reuse the space/document authorization.

CREATE TABLE oa_document_comment (
  id            BIGINT        NOT NULL                   COMMENT '评论ID',
  tenant_id     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id   BIGINT        NOT NULL                   COMMENT '文档ID',
  parent_id     BIGINT        NOT NULL DEFAULT 0         COMMENT '父评论ID(0=一级评论)',
  content       VARCHAR(1000) NOT NULL                   COMMENT '评论内容',
  create_dept   BIGINT        DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT        DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT        DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME      DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500)  DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_comment_document (document_id, create_time),
  KEY idx_comment_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档评论';

CREATE TABLE oa_document_like (
  id            BIGINT       NOT NULL                   COMMENT '点赞ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id   BIGINT       NOT NULL                   COMMENT '文档ID',
  user_id       BIGINT       NOT NULL                   COMMENT '点赞人账号ID',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_like (document_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档点赞';

CREATE TABLE oa_document_favorite (
  id            BIGINT       NOT NULL                   COMMENT '收藏ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  document_id   BIGINT       NOT NULL                   COMMENT '文档ID',
  user_id       BIGINT       NOT NULL                   COMMENT '收藏人账号ID',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_favorite (document_id, user_id),
  KEY idx_favorite_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档收藏';

-- Menu: personal favorites page (object-level endpoints need no extra buttons).
INSERT INTO sys_menu VALUES(2860, '我的收藏', 2500, 9, 'favorite', 'knowledge/favorite/index', '', 1, 0, 'C', '0', '0', '', 'star', 100, 1, sysdate(), null, null, '收藏的文档');
INSERT INTO sys_role_menu VALUES(20, 2860);
INSERT INTO sys_role_menu VALUES(21, 2860);
INSERT INTO sys_role_menu VALUES(22, 2860);
INSERT INTO sys_role_menu VALUES(23, 2860);
INSERT INTO sys_role_menu VALUES(24, 2860);
INSERT INTO sys_role_menu VALUES(25, 2860);
