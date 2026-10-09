package org.dromara.agentoa.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.vo.OaDeptTreeVo;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.service.IHrDeptService;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.domain.SysPost;
import org.dromara.system.domain.bo.SysDeptBo;
import org.dromara.system.domain.vo.SysDeptVo;
import org.dromara.system.mapper.SysPostMapper;
import org.dromara.system.service.ISysDeptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门组织服务实现。委托 ruoyi-system 部门能力，补充业务引用与循环保护。
 */
@RequiredArgsConstructor
@Service
public class HrDeptServiceImpl implements IHrDeptService {

    private final ISysDeptService deptService;
    private final SysPostMapper postMapper;
    private final OaEmployeeMapper employeeMapper;

    @Override
    public List<OaDeptTreeVo> selectDeptTree() {
        List<SysDeptVo> depts = deptService.selectDeptList(new SysDeptBo());
        Map<Long, OaDeptTreeVo> nodes = new LinkedHashMap<>();
        for (SysDeptVo dept : depts) {
            OaDeptTreeVo node = new OaDeptTreeVo();
            node.setId(dept.getDeptId());
            node.setName(dept.getDeptName());
            node.setParentId(dept.getParentId());
            node.setSort(dept.getOrderNum());
            node.setStatus(dept.getStatus());
            nodes.put(node.getId(), node);
        }
        List<OaDeptTreeVo> roots = new ArrayList<>();
        for (OaDeptTreeVo node : nodes.values()) {
            OaDeptTreeVo parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public SysDeptVo selectDeptById(Long deptId) {
        return deptService.selectDeptById(deptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertDept(SysDeptBo bo) {
        if (!deptService.checkDeptNameUnique(bo)) {
            throw new ServiceException("部门名称'" + bo.getDeptName() + "'已存在", 400);
        }
        deptService.insertDept(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Long deptId, SysDeptBo bo) {
        bo.setDeptId(deptId);
        SysDeptVo existing = deptService.selectDeptById(deptId);
        if (existing == null) {
            throw new ServiceException("部门不存在", 404);
        }
        assertNoCycle(deptId, bo.getParentId());
        if (!deptService.checkDeptNameUnique(bo)) {
            throw new ServiceException("部门名称'" + bo.getDeptName() + "'已存在", 409);
        }
        if (SystemConstants.DISABLE.equals(bo.getStatus()) && countActiveEmployees(deptId) > 0) {
            throw new ServiceException("部门仍有在职员工，不能停用", 409);
        }
        deptService.updateDept(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeptStatus(Long deptId, String status) {
        SysDeptVo existing = deptService.selectDeptById(deptId);
        if (existing == null) {
            throw new ServiceException("部门不存在", 404);
        }
        if (SystemConstants.DISABLE.equals(status) && countActiveEmployees(deptId) > 0) {
            throw new ServiceException("部门仍有在职员工，不能停用", 409);
        }
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptId(deptId);
        bo.setDeptName(existing.getDeptName());
        bo.setParentId(existing.getParentId());
        bo.setOrderNum(existing.getOrderNum());
        bo.setStatus(status);
        deptService.updateDept(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long deptId) {
        if (deptService.hasChildByDeptId(deptId)) {
            throw new ServiceException("存在下级部门，不能删除", 409);
        }
        if (countActiveEmployees(deptId) > 0) {
            throw new ServiceException("部门存在员工引用，不能删除", 409);
        }
        long posts = postMapper.selectCount(new LambdaQueryWrapper<SysPost>().eq(SysPost::getDeptId, deptId));
        if (posts > 0) {
            throw new ServiceException("部门存在岗位引用，不能删除", 409);
        }
        deptService.deleteDeptById(deptId);
    }

    @Override
    public Map<String, Long> selectDeptIdByName() {
        Map<String, Long> byName = new HashMap<>();
        for (SysDeptVo dept : deptService.selectDeptList(new SysDeptBo())) {
            if (StringUtils.isNotBlank(dept.getDeptName())) {
                byName.put(dept.getDeptName().trim(), dept.getDeptId());
            }
        }
        return byName;
    }

    /** 上级部门是自身或自身子孙时构成循环。 */
    private void assertNoCycle(Long deptId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(deptId)) {
            throw new ServiceException("上级部门不能是自己", 400);
        }
        SysDeptVo parent = deptService.selectDeptById(parentId);
        if (parent == null) {
            throw new ServiceException("上级部门不存在", 404);
        }
        if (parent.getAncestors() != null
            && List.of(parent.getAncestors().split(",")).contains(String.valueOf(deptId))) {
            throw new ServiceException("上级部门不能是自己的子部门", 400);
        }
    }

    private long countActiveEmployees(Long deptId) {
        return employeeMapper.selectCount(new LambdaQueryWrapper<OaEmployee>()
            .eq(OaEmployee::getDeptId, deptId)
            .notIn(OaEmployee::getStatus, "LEFT", "DISABLED", "DRAFT"));
    }
}
