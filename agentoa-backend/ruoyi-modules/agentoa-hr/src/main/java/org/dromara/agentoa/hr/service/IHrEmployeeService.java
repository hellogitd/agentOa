package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeBo;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeProfileBo;
import org.dromara.agentoa.hr.domain.bo.OaOffboardBo;
import org.dromara.agentoa.hr.domain.bo.OaOnboardBo;
import org.dromara.agentoa.hr.domain.bo.OaRegularizeBo;
import org.dromara.agentoa.hr.domain.bo.OaStatusBo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportFullVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeHistoryVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 员工档案与生命周期服务（API 规范 3.3/3.4/3.5/3.7）。
 */
public interface IHrEmployeeService {

    PageVo<OaEmployeeVo> selectPageEmployees(OaEmployeeBo query, HrPageQuery page);

    List<OaEmployeeVo> selectEmployees(OaEmployeeBo query);

    /** 单条读取，含对象级越权校验 */
    OaEmployeeVo selectEmployee(Long employeeId);

    /** 变动历史（/employees/{id}/changes） */
    List<OaEmployeeHistoryVo> selectChanges(Long employeeId);

    /** 新建草稿档案，工号可由服务端生成 */
    OaEmployeeVo createEmployee(OaEmployeeBo bo);

    /** HR 字段修改；状态只能通过生命周期命令变更 */
    OaEmployeeVo updateEmployee(Long employeeId, OaEmployeeBo bo);

    /** 自助字段（手机、邮箱）修改 */
    OaEmployeeVo updateProfile(Long employeeId, OaEmployeeProfileBo bo);

    /** 仅草稿档案可删除 */
    void deleteEmployee(Long employeeId);

    /** 入职：建档案 + DRAFT -> PROBATION（一次命令） */
    OaEmployeeVo onboard(OaOnboardBo bo);

    /** 转正：PROBATION -> ACTIVE */
    OaEmployeeVo regularize(OaRegularizeBo bo);

    /** 离职：PROBATION/ACTIVE/LEAVE_PENDING -> LEFT，同事务冻结账号 */
    OaEmployeeVo offboard(OaOffboardBo bo);

    /** 状态端点：按目标状态路由到生命周期命令或停用/待离职 */
    OaEmployeeVo updateStatus(Long employeeId, OaStatusBo bo);

    /** 批量导入；存在任一错误行时不导入任何数据 */
    OaImportReportVo importEmployees(List<OaEmployeeImportVo> rows);

    /** 导出数据（不含敏感字段） */
    List<OaEmployeeExportVo> exportEmployees(OaEmployeeBo query);

    /** 导出数据（含身份证、手机号；需 hr:employee:sensitive 授权） */
    List<OaEmployeeExportFullVo> exportEmployeesFull(OaEmployeeBo query);
}
