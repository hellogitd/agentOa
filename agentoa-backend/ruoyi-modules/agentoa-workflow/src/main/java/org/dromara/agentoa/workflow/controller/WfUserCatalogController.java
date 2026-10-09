package org.dromara.agentoa.workflow.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.AssignableUserVo;
import org.dromara.agentoa.workflow.service.support.AssignableUserCatalog;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 选人目录（docs/05 4.6）：登录即可检索的可选办理人列表，H5/管理端选人组件共用。
 * <p>
 * 不校验 wf:* 权限（普通员工发起自选、转办时也要能选人），只回启用账号的
 * {@code userId/name/deptName}，不暴露手机号/邮箱等敏感字段。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf")
public class WfUserCatalogController {

    private final AssignableUserCatalog assignableUserCatalog;

    /** 可选办理人检索：keyword 按账号名/昵称模糊匹配，登录即可调用 */
    @GetMapping("/assignable-users")
    public R<PageVo<AssignableUserVo>> assignableUsers(@RequestParam(required = false) String keyword,
                                                      PageQuery page) {
        return R.ok(assignableUserCatalog.search(keyword, page));
    }
}
