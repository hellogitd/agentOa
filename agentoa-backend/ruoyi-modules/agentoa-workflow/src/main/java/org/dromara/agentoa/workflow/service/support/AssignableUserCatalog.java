package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.AssignableUserVo;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.springframework.stereotype.Component;

/**
 * 可选办理人目录（选人基建）：登录即可检索的最小身份目录，供 H5/管理端选人组件共用。
 * 只回启用账号（{@code sys_user.status='0' AND del_flag='0'}），不暴露敏感字段；
 * 不受 HR {@code data_scope} 收窄影响（普通员工发起自选时也要能选到同事）。
 */
@Component
@RequiredArgsConstructor
public class AssignableUserCatalog {

    private final WorkflowIdentityReadMapper identityMapper;

    public PageVo<AssignableUserVo> search(String keyword, PageQuery page) {
        String normalized = keyword == null ? "" : keyword.trim();
        int pageNum = page.safePageNum();
        int pageSize = page.safePageSize();
        long total = identityMapper.countAssignableUsers(normalized);
        java.util.List<AssignableUserVo> records = total == 0 ? java.util.List.of()
            : identityMapper.selectAssignableUsers(normalized, (pageNum - 1) * pageSize, pageSize);
        return PageVo.of(records, total, pageNum, pageSize);
    }
}
