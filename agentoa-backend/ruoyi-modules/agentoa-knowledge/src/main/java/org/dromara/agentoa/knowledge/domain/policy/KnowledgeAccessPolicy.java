package org.dromara.agentoa.knowledge.domain.policy;

import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;

import java.util.Set;

/**
 * 模块 6 权限矩阵的纯函数实现（docs/16 / docs/02 KB-08）。
 * <p>
 * 数据可见性只认空间成员授权、空间类型与文档级 ACL：默认无权限，管理员也需审计授权；
 * 接口级写操作另需 kn:* 按钮权限（@SaCheckPermission）。
 */
public final class KnowledgeAccessPolicy {

    public static final String PERM_SPACE_ADD = "kn:space:add";
    public static final String PERM_SPACE_EDIT = "kn:space:edit";
    public static final String PERM_SPACE_REMOVE = "kn:space:remove";
    public static final String PERM_MEMBER_ADD = "kn:member:add";
    public static final String PERM_MEMBER_EDIT = "kn:member:edit";
    public static final String PERM_MEMBER_REMOVE = "kn:member:remove";
    public static final String PERM_DOC_ADD = "kn:doc:add";
    public static final String PERM_DOC_EDIT = "kn:doc:edit";
    public static final String PERM_DOC_REMOVE = "kn:doc:remove";
    public static final String PERM_DOC_RESTORE = "kn:doc:restore";
    public static final String PERM_FILE_UPLOAD = "kn:file:upload";
    public static final String PERM_FILE_REMOVE = "kn:file:remove";

    private KnowledgeAccessPolicy() {
    }

    /**
     * 解析用户在空间上的有效角色（docs/02 KB-08 继承规则）。
     *
     * @param membershipRole 成员表角色，可空
     * @param spaceType      空间类型（1公开 2私密 3团队）
     * @param creatorId      空间创建人
     */
    public static SpaceRole resolveSpaceRole(Long actorUserId, String membershipRole,
                                             int spaceType, Long creatorId) {
        if (membershipRole != null && !membershipRole.isBlank()) {
            return SpaceRole.from(membershipRole);
        }
        if (spaceType == 1) {
            return SpaceRole.VIEWER;
        }
        if (spaceType == 2 && actorUserId != null && actorUserId.equals(creatorId)) {
            return SpaceRole.OWNER;
        }
        return null;
    }

    /** 文档有效角色 = 空间角色与文档 ACL 取较高者 */
    public static SpaceRole effectiveRole(SpaceRole spaceRole, SpaceRole aclRole) {
        return SpaceRole.max(spaceRole, aclRole);
    }

    public static boolean canView(SpaceRole role) {
        return role != null;
    }

    public static boolean canEdit(SpaceRole role) {
        return role == SpaceRole.OWNER || role == SpaceRole.EDITOR;
    }

    public static boolean canComment(SpaceRole role) {
        return role != null;
    }

    public static boolean canDownload(SpaceRole role) {
        return role != null;
    }

    public static boolean canManage(SpaceRole role) {
        return role == SpaceRole.OWNER;
    }

    /** 接口级按钮权限判定 */
    public static boolean hasPerm(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }
}
