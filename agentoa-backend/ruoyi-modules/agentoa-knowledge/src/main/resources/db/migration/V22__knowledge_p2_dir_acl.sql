-- P1 批次二 · 交付包 F（KB-08 目录 ACL）
-- 迁移版本按实施顺序分配：C=V17、E=V18、A=V19、B=V20、D=V21、F=V22（docs/09 第 16 节）

-- 节点类型：1=文档 2=目录（兼容旧行=1）
ALTER TABLE oa_document ADD COLUMN node_type TINYINT DEFAULT 1 COMMENT '节点类型（1文档 2目录）';

-- 目录级覆盖开关：1=仅目录 ACL 生效（收紧，忽略空间角色与祖先放宽）
ALTER TABLE oa_document_acl ADD COLUMN override_only TINYINT DEFAULT 0 COMMENT '目录覆盖开关（1=仅本目录 ACL 生效）';

-- ACL 管理权限菜单
INSERT INTO sys_menu VALUES(2900, '目录 ACL 管理', 2200, 5, '', '', '', 1, 0, 'F', '0', '0', 'kn:doc:acl', '#', 100, 1, sysdate(), null, null, '目录/文档 ACL 管理');
