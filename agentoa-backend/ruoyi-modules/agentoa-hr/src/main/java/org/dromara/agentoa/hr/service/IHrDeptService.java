package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.vo.OaDeptTreeVo;
import org.dromara.system.domain.bo.SysDeptBo;
import org.dromara.system.domain.vo.SysDeptVo;

import java.util.List;
import java.util.Map;

/**
 * 部门组织服务（API 规范 3.1）。部门数据沿用 sys_dept，本服务补充业务引用约束。
 */
public interface IHrDeptService {

    /** 部门树（含子节点），结构见 OaDeptTreeVo */
    List<OaDeptTreeVo> selectDeptTree();

    SysDeptVo selectDeptById(Long deptId);

    void insertDept(SysDeptBo bo);

    /** 更新含循环引用与停用保护校验 */
    void updateDept(Long deptId, SysDeptBo bo);

    /** 启停用 */
    void updateDeptStatus(Long deptId, String status);

    /** 删除前检查子部门、员工与岗位引用 */
    void deleteDept(Long deptId);

    /** 部门名称到 ID 的映射（导入解析用） */
    Map<String, Long> selectDeptIdByName();
}
