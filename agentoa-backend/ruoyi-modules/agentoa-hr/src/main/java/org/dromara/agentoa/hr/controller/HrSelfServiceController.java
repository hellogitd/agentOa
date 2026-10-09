package org.dromara.agentoa.hr.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.OaContractBo;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeProfileBo;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.agentoa.hr.domain.vo.OaSelfProfileVo;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;
import org.dromara.agentoa.hr.service.IHrContractService;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.hr.service.IHrExperienceService;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工自助接口（需求 HR-10：个人信息维护、经历与合同查看）。
 * <p>
 * 只作用于当前登录用户关联的员工档案，自助字段仅手机与邮箱。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/profile")
public class HrSelfServiceController {

    private final IHrEmployeeService employeeService;
    private final IHrExperienceService experienceService;
    private final IHrContractService contractService;
    private final OaEmployeeMapper employeeMapper;

    /** 本人档案 + 教育/工作经历 + 合同 */
    @GetMapping("/self")
    public R<OaSelfProfileVo> self() {
        OaEmployee employee = requireOwnEmployee();
        OaSelfProfileVo vo = new OaSelfProfileVo();
        vo.setEmployee(employeeService.selectEmployee(employee.getId()));
        List<OaEducationVo> educations = experienceService.selectEducations(employee.getId());
        vo.setEducations(educations);
        List<OaWorkVo> works = experienceService.selectWorks(employee.getId());
        vo.setWorks(works);
        OaContractBo query = new OaContractBo();
        query.setEmployeeId(employee.getId());
        List<OaContractVo> contracts = contractService.selectContracts(query);
        vo.setContracts(contracts);
        return R.ok(vo);
    }

    /** 自助字段修改（手机、邮箱） */
    @RepeatSubmit()
    @PutMapping("/self")
    public R<Void> updateSelf(@Validated @RequestBody OaEmployeeProfileBo bo) {
        OaEmployee employee = requireOwnEmployee();
        employeeService.updateProfile(employee.getId(), bo);
        return R.ok();
    }

    private OaEmployee requireOwnEmployee() {
        Long userId = LoginHelper.getUserId();
        OaEmployee employee = employeeMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OaEmployee>()
                .eq(OaEmployee::getUserId, userId));
        if (employee == null) {
            throw new ServiceException("当前账号未关联员工档案", 404);
        }
        return employee;
    }
}
