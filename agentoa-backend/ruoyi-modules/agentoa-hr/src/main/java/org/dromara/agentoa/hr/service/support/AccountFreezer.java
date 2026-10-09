package org.dromara.agentoa.hr.service.support;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.system.domain.SysUser;
import org.dromara.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 离职完成时同事务冻结系统账号，并注销该账号全部会话（旧 token 后续请求由账号守卫拒绝）。
 */
@Component
@RequiredArgsConstructor
public class AccountFreezer {

    private final SysUserMapper userMapper;

    /**
     * @return 是否冻结了账号（账号不存在时返回 false）
     */
    public boolean freeze(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        user.setStatus(SystemConstants.DISABLE);
        userMapper.updateById(user);
        logoutSessions(userId);
        return true;
    }

    private void logoutSessions(Long userId) {
        try {
            StpUtil.logout(UserType.SYS_USER.getUserType() + ":" + userId);
        } catch (Exception ignored) {
            // 无在线会话不影响离职事务
        }
    }
}
