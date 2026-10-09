package org.dromara.agentoa.hr.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaEmployeeEducation;
import org.dromara.agentoa.hr.domain.OaEmployeeWork;
import org.dromara.agentoa.hr.domain.bo.OaEducationBo;
import org.dromara.agentoa.hr.domain.bo.OaWorkBo;
import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;
import org.dromara.agentoa.hr.mapper.OaEmployeeEducationMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeWorkMapper;
import org.dromara.agentoa.hr.service.IHrExperienceService;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 员工教育/工作经历服务实现（需求 HR-03 P1、HR-10 员工自助）。
 * <p>
 * 约束：HR 与管理员可维护任意员工；员工本人可维护自己的经历；
 * 记录必须归属路径中的员工，禁止跨员工写入。
 */
@RequiredArgsConstructor
@Service
public class HrExperienceServiceImpl implements IHrExperienceService {

    private final OaEmployeeEducationMapper educationMapper;
    private final OaEmployeeWorkMapper workMapper;
    private final OaEmployeeMapper employeeMapper;

    // ---------------------------------------------------------------- 教育经历

    @Override
    public List<OaEducationVo> selectEducations(Long employeeId) {
        requireEmployee(employeeId);
        checkViewAccess(employeeId);
        return educationMapper.selectByEmployeeId(employeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEducationVo createEducation(Long employeeId, OaEducationBo bo) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        OaEmployeeEducation entity = MapstructUtils.convert(bo, OaEmployeeEducation.class);
        entity.setId(null);
        entity.setEmployeeId(employeeId);
        educationMapper.insert(entity);
        return educationMapper.selectVoById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEducationVo updateEducation(Long employeeId, Long educationId, OaEducationBo bo) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        OaEmployeeEducation existing = requireEducation(educationId, employeeId);
        OaEmployeeEducation entity = MapstructUtils.convert(bo, OaEmployeeEducation.class);
        entity.setId(existing.getId());
        entity.setEmployeeId(employeeId);
        educationMapper.updateById(entity);
        return educationMapper.selectVoById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEducation(Long employeeId, Long educationId) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        requireEducation(educationId, employeeId);
        educationMapper.deleteById(educationId);
    }

    // ---------------------------------------------------------------- 工作经历

    @Override
    public List<OaWorkVo> selectWorks(Long employeeId) {
        requireEmployee(employeeId);
        checkViewAccess(employeeId);
        return workMapper.selectByEmployeeId(employeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaWorkVo createWork(Long employeeId, OaWorkBo bo) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        OaEmployeeWork entity = MapstructUtils.convert(bo, OaEmployeeWork.class);
        entity.setId(null);
        entity.setEmployeeId(employeeId);
        workMapper.insert(entity);
        return workMapper.selectVoById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaWorkVo updateWork(Long employeeId, Long workId, OaWorkBo bo) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        OaEmployeeWork existing = requireWork(workId, employeeId);
        OaEmployeeWork entity = MapstructUtils.convert(bo, OaEmployeeWork.class);
        entity.setId(existing.getId());
        entity.setEmployeeId(employeeId);
        workMapper.updateById(entity);
        return workMapper.selectVoById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(Long employeeId, Long workId) {
        OaEmployee employee = requireEmployee(employeeId);
        checkEditAccess(employee);
        requireWork(workId, employeeId);
        workMapper.deleteById(workId);
    }

    // ---------------------------------------------------------------- 内部方法

    private OaEmployee requireEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new ServiceException("员工不能为空", 400);
        }
        OaEmployee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new ServiceException("员工不存在", 404);
        }
        return employee;
    }

    private OaEmployeeEducation requireEducation(Long educationId, Long employeeId) {
        OaEmployeeEducation entity = educationMapper.selectById(educationId);
        if (entity == null || !employeeId.equals(entity.getEmployeeId())) {
            throw new ServiceException("教育经历不存在", 404);
        }
        return entity;
    }

    private OaEmployeeWork requireWork(Long workId, Long employeeId) {
        OaEmployeeWork entity = workMapper.selectById(workId);
        if (entity == null || !employeeId.equals(entity.getEmployeeId())) {
            throw new ServiceException("工作经历不存在", 404);
        }
        return entity;
    }

    private void checkViewAccess(Long employeeId) {
        OaEmployee employee = requireEmployee(employeeId);
        LoginUser user = LoginHelper.getLoginUser();
        boolean allowed = HrAccessPolicy.canView(isSuperAdmin(), isHr(),
            user == null ? null : user.getUserId(), user == null ? null : user.getDeptId(),
            employee.getUserId(), employee.getDeptId(), employee.getDirectLeaderId());
        if (!allowed) {
            throw new ServiceException("没有权限访问员工经历", 403);
        }
    }

    private void checkEditAccess(OaEmployee employee) {
        LoginUser user = LoginHelper.getLoginUser();
        boolean allowed = HrAccessPolicy.canEditSelfService(isSuperAdmin(), isHr(),
            user == null ? null : user.getUserId(), employee.getUserId());
        if (!allowed) {
            throw new ServiceException("没有权限维护员工经历", 403);
        }
    }

    private boolean isSuperAdmin() {
        return LoginHelper.isSuperAdmin();
    }

    private boolean isHr() {
        LoginUser user = LoginHelper.getLoginUser();
        Set<String> permissions = user == null ? null : user.getMenuPermission();
        return isSuperAdmin() || HrAccessPolicy.canManageHrRecords(false, permissions);
    }
}
