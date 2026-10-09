package org.dromara.agentoa.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaEmployeeHistory;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.agentoa.hr.domain.OaOffboarding;
import org.dromara.agentoa.hr.domain.OaOnboarding;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeBo;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeProfileBo;
import org.dromara.agentoa.hr.domain.bo.OaOffboardBo;
import org.dromara.agentoa.hr.domain.bo.OaOnboardBo;
import org.dromara.agentoa.hr.domain.bo.OaRegularizeBo;
import org.dromara.agentoa.hr.domain.bo.OaStatusBo;
import org.dromara.agentoa.hr.domain.enums.EmployeeStatus;
import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportFullVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeHistoryVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.mapper.OaEmployeeHistoryMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaJobPositionMapper;
import org.dromara.agentoa.hr.mapper.OaOffboardingMapper;
import org.dromara.agentoa.hr.mapper.OaOnboardingMapper;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.hr.service.support.AccountFreezer;
import org.dromara.agentoa.hr.service.support.EmployeeNumberGenerator;
import org.dromara.agentoa.hr.service.support.HrImportValidator;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.domain.bo.SysDeptBo;
import org.dromara.system.domain.vo.SysDeptVo;
import org.dromara.system.service.ISysDeptService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 员工档案与生命周期服务实现。
 * <p>
 * 约束：状态变更只经生命周期命令且写历史；转正、离职重复执行只产生一个生效事件；
 * 离职/停用的账号冻结与业务完成同事务；导入存在错误行时整批不落库。
 */
@RequiredArgsConstructor
@Service
public class HrEmployeeServiceImpl implements IHrEmployeeService {

    private static final int GENERATE_RETRY = 5;
    private static final Set<String> ORDER_WHITELIST =
        Set.of("employeeNo", "name", "entryDate", "status", "createTime");

    private final OaEmployeeMapper baseMapper;
    private final OaEmployeeHistoryMapper historyMapper;
    private final OaOnboardingMapper onboardingMapper;
    private final OaOffboardingMapper offboardingMapper;
    private final OaJobPositionMapper positionMapper;
    private final ISysDeptService deptService;
    private final EmployeeNumberGenerator numberGenerator;
    private final AccountFreezer accountFreezer;

    // ---------------------------------------------------------------- 查询

    @Override
    public PageVo<OaEmployeeVo> selectPageEmployees(OaEmployeeBo query, HrPageQuery page) {
        Page<OaEmployee> mpPage = new Page<>(page.safePageNum(), page.safePageSize());
        Page<OaEmployee> result = baseMapper.selectPage(mpPage, buildQueryWrapper(query, page));
        List<OaEmployeeVo> records = toVoList(result.getRecords());
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<OaEmployeeVo> selectEmployees(OaEmployeeBo query) {
        return toVoList(baseMapper.selectList(buildQueryWrapper(query, new HrPageQuery())));
    }

    @Override
    public OaEmployeeVo selectEmployee(Long employeeId) {
        OaEmployee employee = requireEmployee(employeeId);
        checkViewAccess(employee);
        return toVo(employee);
    }

    @Override
    public List<OaEmployeeHistoryVo> selectChanges(Long employeeId) {
        OaEmployee employee = requireEmployee(employeeId);
        checkViewAccess(employee);
        return historyMapper.selectHistoryByEmployeeId(employeeId);
    }

    // ---------------------------------------------------------------- 档案维护

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo createEmployee(OaEmployeeBo bo) {
        if (!HrAccessPolicy.canEdit(isSuperAdmin(), isHr())) {
            throw new ServiceException("没有权限修改员工档案", 403);
        }
        if (StringUtils.isNotBlank(bo.getStatus()) && !EmployeeStatus.DRAFT.name().equals(bo.getStatus())) {
            throw new ServiceException("新建员工只能是草稿状态", 400);
        }
        validateReferences(bo.getDeptId(), bo.getPostId());
        OaEmployee employee = MapstructUtils.convert(bo, OaEmployee.class);
        employee.setId(null);
        employee.setStatus(EmployeeStatus.DRAFT.name());
        employee.setRegularDate(null);
        employee.setLeaveDate(null);
        if (StringUtils.isBlank(employee.getEmployeeNo())) {
            insertWithGeneratedNumber(employee);
        } else {
            employee.setEmployeeNo(EmployeeNumberGenerator.normalize(employee.getEmployeeNo()));
            if (baseMapper.exists(new LambdaQueryWrapper<OaEmployee>().eq(OaEmployee::getEmployeeNo, employee.getEmployeeNo()))) {
                throw new ServiceException("工号'" + employee.getEmployeeNo() + "'已存在", 409);
            }
            baseMapper.insert(employee);
        }
        writeHistory(employee, "CREATE", "CREATE:" + employee.getId(), null, employee.getStatus(), "建立员工档案", null);
        return toVo(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo updateEmployee(Long employeeId, OaEmployeeBo bo) {
        OaEmployee existing = requireEmployee(employeeId);
        checkEditAccess(existing);
        if (StringUtils.isNotBlank(bo.getStatus()) && !bo.getStatus().equals(existing.getStatus())) {
            throw new ServiceException("状态只能通过生命周期命令变更", 400);
        }
        validateReferences(bo.getDeptId(), bo.getPostId());
        OaEmployee employee = MapstructUtils.convert(bo, OaEmployee.class);
        employee.setId(employeeId);
        employee.setStatus(null);
        employee.setUserId(null);
        employee.setEmployeeNo(null);
        employee.setRegularDate(null);
        employee.setLeaveDate(null);
        baseMapper.updateById(employee);
        return toVo(requireEmployee(employeeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo updateProfile(Long employeeId, OaEmployeeProfileBo bo) {
        OaEmployee existing = requireEmployee(employeeId);
        if (!HrAccessPolicy.canEditSelfService(isSuperAdmin(), isHr(), currentUserId(), existing.getUserId())) {
            throw new ServiceException("没有权限修改该员工自助信息", 403);
        }
        if (EmployeeStatus.LEFT.name().equals(existing.getStatus())
            || EmployeeStatus.DISABLED.name().equals(existing.getStatus())) {
            throw new ServiceException("已离职或已停用员工不能修改档案", 409);
        }
        OaEmployee employee = new OaEmployee();
        employee.setId(employeeId);
        employee.setPhone(bo.getPhone());
        employee.setEmail(bo.getEmail());
        baseMapper.updateById(employee);
        return toVo(requireEmployee(employeeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployee(Long employeeId) {
        OaEmployee existing = requireEmployee(employeeId);
        checkEditAccess(existing);
        if (!EmployeeStatus.DRAFT.name().equals(existing.getStatus())) {
            throw new ServiceException("仅草稿状态员工可删除", 409);
        }
        baseMapper.deleteById(employeeId);
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo onboard(OaOnboardBo bo) {
        checkLifecycleAccess();
        validateReferences(bo.getDeptId(), bo.getPostId());
        OaEmployee employee = new OaEmployee();
        employee.setName(bo.getName().trim());
        employee.setGender(StringUtils.isBlank(bo.getGender()) ? "2" : bo.getGender());
        employee.setBirthDate(bo.getBirthDate());
        employee.setIdCardNo(bo.getIdCardNo());
        employee.setPhone(bo.getPhone());
        employee.setEmail(bo.getEmail());
        employee.setDeptId(bo.getDeptId());
        employee.setPostId(bo.getPostId());
        employee.setPositionLevel(bo.getPositionLevel());
        employee.setDirectLeaderId(bo.getDirectLeaderId());
        employee.setStatus(EmployeeStatus.DRAFT.name());
        employee.setRemark(bo.getRemark());
        if (StringUtils.isBlank(bo.getEmployeeNo())) {
            insertWithGeneratedNumber(employee);
        } else {
            employee.setEmployeeNo(EmployeeNumberGenerator.normalize(bo.getEmployeeNo()));
            if (baseMapper.exists(new LambdaQueryWrapper<OaEmployee>().eq(OaEmployee::getEmployeeNo, employee.getEmployeeNo()))) {
                throw new ServiceException("工号'" + employee.getEmployeeNo() + "'已存在", 409);
            }
            baseMapper.insert(employee);
        }
        writeHistory(employee, "CREATE", "CREATE:" + employee.getId(), null, employee.getStatus(), "建立员工档案", null);
        applyOnboarding(employee, bo.getEntryDate(), bo.getProbationEndDate(), bo.getWorkflowInstanceId());
        return toVo(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo regularize(OaRegularizeBo bo) {
        checkLifecycleAccess();
        OaEmployee employee = requireEmployee(bo.getEmployeeId());
        if (EmployeeStatus.ACTIVE.name().equals(employee.getStatus())) {
            return toVo(employee);
        }
        assertTransition(employee, EmployeeStatus.ACTIVE, "转正");
        String from = employee.getStatus();
        LocalDate regularDate = bo.getRegularDate() != null ? bo.getRegularDate() : LocalDate.now();
        employee.setStatus(EmployeeStatus.ACTIVE.name());
        employee.setRegularDate(regularDate);
        if (StringUtils.isNotBlank(bo.getWorkflowInstanceId())) {
            employee.setWorkflowInstanceId(bo.getWorkflowInstanceId());
        }
        baseMapper.updateById(employee);
        insertUniqueEvent(() -> writeHistory(employee, "PROBATION", "PROBATION:" + employee.getId(),
            from, employee.getStatus(), StringUtils.defaultIfBlank(bo.getRemark(), "办理转正"), bo.getWorkflowInstanceId()), "转正");
        return toVo(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo offboard(OaOffboardBo bo) {
        checkLifecycleAccess();
        OaEmployee employee = requireEmployee(bo.getEmployeeId());
        if (offboardingMapper.selectByEmployeeId(employee.getId()) != null) {
            return toVo(employee);
        }
        assertTransition(employee, EmployeeStatus.LEFT, "离职");
        LocalDate lastDay = bo.getLastWorkingDay() != null ? bo.getLastWorkingDay() : LocalDate.now();
        OaOffboarding record = new OaOffboarding();
        record.setEmployeeId(employee.getId());
        record.setEventId("OFFBOARDING:" + employee.getId());
        record.setReason(bo.getReason());
        record.setLastWorkingDay(lastDay);
        record.setStatus("COMPLETED");
        record.setWorkflowInstanceId(bo.getWorkflowInstanceId());
        record.setOperatorUserId(currentUserId());
        record.setOperateTime(new Date());
        record.setCreateTime(new Date());

        boolean frozen = accountFreezer.freeze(employee.getUserId());
        record.setAccountFrozen(frozen ? 1 : 0);
        insertUniqueEvent(() -> offboardingMapper.insert(record), "离职");

        String from = employee.getStatus();
        employee.setStatus(EmployeeStatus.LEFT.name());
        employee.setLeaveDate(lastDay);
        if (StringUtils.isNotBlank(bo.getWorkflowInstanceId())) {
            employee.setWorkflowInstanceId(bo.getWorkflowInstanceId());
        }
        baseMapper.updateById(employee);
        writeHistory(employee, "OFFBOARDING", record.getEventId(), from, employee.getStatus(),
            StringUtils.defaultIfBlank(bo.getReason(), "办理离职"), bo.getWorkflowInstanceId());
        return toVo(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaEmployeeVo updateStatus(Long employeeId, OaStatusBo bo) {
        OaEmployee employee = requireEmployee(employeeId);
        return switch (EmployeeStatus.from(bo.getStatus())) {
            case PROBATION -> applyOnboarding(employee,
                employee.getEntryDate() != null ? employee.getEntryDate() : LocalDate.now(),
                employee.getProbationEndDate(), null);
            case ACTIVE -> regularize(buildRegularize(employeeId, null));
            case LEFT -> offboard(buildOffboard(employeeId, bo));
            case LEAVE_PENDING -> markLeavePending(employee, bo);
            case DISABLED -> disable(employee, bo);
            case DRAFT -> throw new ServiceException("不能通过状态端点回退到草稿", 400);
        };
    }

    // ---------------------------------------------------------------- 导入导出

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaImportReportVo importEmployees(List<OaEmployeeImportVo> rows) {
        checkImportExportAccess();
        boolean sensitiveAllowed = HrAccessPolicy.canViewSensitive(isSuperAdmin(), currentPermissions());
        Set<String> existingNos = new HashSet<>();
        for (OaEmployee employee : baseMapper.selectList(new LambdaQueryWrapper<OaEmployee>()
            .select(OaEmployee::getEmployeeNo))) {
            if (StringUtils.isNotBlank(employee.getEmployeeNo())) {
                existingNos.add(employee.getEmployeeNo().toUpperCase());
            }
        }
        Map<String, Long> deptByName = deptIdByName();
        Map<String, Long> postByCode = positionIdByCode();
        OaImportReportVo report = HrImportValidator.validate(rows, existingNos, deptByName, postByCode, sensitiveAllowed);
        if (report.getFailedRows() > 0) {
            return report;
        }
        if (rows != null) {
            for (OaEmployeeImportVo row : rows) {
                OaEmployee employee = new OaEmployee();
                employee.setName(row.getName().trim());
                employee.setGender(StringUtils.isBlank(row.getGender()) ? "2" : row.getGender().trim());
                employee.setIdCardNo(StringUtils.isBlank(row.getIdCard()) ? null : row.getIdCard().trim());
                employee.setPhone(StringUtils.isBlank(row.getPhone()) ? null : row.getPhone().trim());
                employee.setEmail(StringUtils.isBlank(row.getEmail()) ? null : row.getEmail().trim());
                employee.setDeptId(deptByName.get(row.getDeptName().trim()));
                employee.setPostId(StringUtils.isBlank(row.getPositionCode())
                    ? null : postByCode.get(row.getPositionCode().trim()));
                employee.setEntryDate(HrImportValidator.parseDate(row.getHireDate()));
                employee.setStatus(EmployeeStatus.DRAFT.name());
                employee.setRemark(row.getRemark());
                if (StringUtils.isBlank(row.getEmployeeNo())) {
                    insertWithGeneratedNumber(employee);
                } else {
                    employee.setEmployeeNo(EmployeeNumberGenerator.normalize(row.getEmployeeNo()));
                    baseMapper.insert(employee);
                }
                writeHistory(employee, "CREATE", "CREATE:" + employee.getId(), null, employee.getStatus(), "导入员工档案", null);
            }
        }
        report.setImported(true);
        return report;
    }

    @Override
    public List<OaEmployeeExportVo> exportEmployees(OaEmployeeBo query) {
        checkImportExportAccess();
        List<OaEmployee> employees = baseMapper.selectList(buildQueryWrapper(query, new HrPageQuery()));
        List<OaEmployeeExportVo> rows = MapstructUtils.convert(employees, OaEmployeeExportVo.class);
        enrichExport(employees, rows);
        return rows;
    }

    @Override
    public List<OaEmployeeExportFullVo> exportEmployeesFull(OaEmployeeBo query) {
        checkImportExportAccess();
        if (!HrAccessPolicy.canViewSensitive(isSuperAdmin(), currentPermissions())) {
            throw new ServiceException("没有权限导出敏感字段", 403);
        }
        List<OaEmployee> employees = baseMapper.selectList(buildQueryWrapper(query, new HrPageQuery()));
        List<OaEmployeeExportFullVo> rows = MapstructUtils.convert(employees, OaEmployeeExportFullVo.class);
        enrichExport(employees, rows);
        return rows;
    }

    // ---------------------------------------------------------------- 内部方法

    private OaEmployeeVo applyOnboarding(OaEmployee employee, LocalDate entryDate,
                                         LocalDate probationEndDate, String workflowInstanceId) {
        if (onboardingMapper.selectByEmployeeId(employee.getId()) != null) {
            return toVo(employee);
        }
        assertTransition(employee, EmployeeStatus.PROBATION, "入职");
        LocalDate hire = entryDate != null ? entryDate : LocalDate.now();
        OaOnboarding record = new OaOnboarding();
        record.setEmployeeId(employee.getId());
        record.setEventId("ONBOARDING:" + employee.getId());
        record.setEntryDate(hire);
        record.setProbationEndDate(probationEndDate);
        record.setDeptId(employee.getDeptId());
        record.setPostId(employee.getPostId());
        record.setStatus("COMPLETED");
        record.setWorkflowInstanceId(workflowInstanceId);
        record.setOperatorUserId(currentUserId());
        record.setOperateTime(new Date());
        record.setCreateTime(new Date());
        insertUniqueEvent(() -> onboardingMapper.insert(record), "入职");

        String from = employee.getStatus();
        employee.setStatus(EmployeeStatus.PROBATION.name());
        employee.setEntryDate(hire);
        employee.setProbationEndDate(probationEndDate);
        if (StringUtils.isNotBlank(workflowInstanceId)) {
            employee.setWorkflowInstanceId(workflowInstanceId);
        }
        baseMapper.updateById(employee);
        writeHistory(employee, "ONBOARDING", record.getEventId(), from, employee.getStatus(), "办理入职", workflowInstanceId);
        return toVo(employee);
    }

    private OaEmployeeVo markLeavePending(OaEmployee employee, OaStatusBo bo) {
        assertTransition(employee, EmployeeStatus.LEAVE_PENDING, "待离职");
        String from = employee.getStatus();
        employee.setStatus(EmployeeStatus.LEAVE_PENDING.name());
        baseMapper.updateById(employee);
        insertUniqueEvent(() -> writeHistory(employee, "LEAVE_PENDING", "LEAVE_PENDING:" + employee.getId(),
            from, employee.getStatus(), StringUtils.defaultIfBlank(bo.getReason(), "标记待离职"), null), "待离职");
        return toVo(employee);
    }

    private OaEmployeeVo disable(OaEmployee employee, OaStatusBo bo) {
        assertTransition(employee, EmployeeStatus.DISABLED, "停用");
        String from = employee.getStatus();
        accountFreezer.freeze(employee.getUserId());
        employee.setStatus(EmployeeStatus.DISABLED.name());
        baseMapper.updateById(employee);
        insertUniqueEvent(() -> writeHistory(employee, "DISABLED", "DISABLED:" + employee.getId(),
            from, employee.getStatus(), StringUtils.defaultIfBlank(bo.getReason(), "停用员工"), null), "停用");
        return toVo(employee);
    }

    private OaRegularizeBo buildRegularize(Long employeeId, LocalDate regularDate) {
        OaRegularizeBo bo = new OaRegularizeBo();
        bo.setEmployeeId(employeeId);
        bo.setRegularDate(regularDate);
        return bo;
    }

    private OaOffboardBo buildOffboard(Long employeeId, OaStatusBo bo) {
        OaOffboardBo offboard = new OaOffboardBo();
        offboard.setEmployeeId(employeeId);
        offboard.setReason(bo.getReason());
        return offboard;
    }

    private OaEmployee requireEmployee(Long employeeId) {
        OaEmployee employee = baseMapper.selectById(employeeId);
        if (employee == null) {
            throw new ServiceException("员工不存在", 404);
        }
        return employee;
    }

    private void assertTransition(OaEmployee employee, EmployeeStatus target, String action) {
        EmployeeStatus from = EmployeeStatus.from(employee.getStatus());
        if (!from.canTransitionTo(target)) {
            throw new ServiceException("当前状态" + from.name() + "不能办理" + action, 409);
        }
    }

    /** 业务事件唯一键冲突（重复提交/并发）时拒绝，保证一次命令只有一个生效事件。 */
    private void insertUniqueEvent(Runnable insert, String action) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new ServiceException("重复的" + action + "请求", 409);
        }
    }

    private void writeHistory(OaEmployee employee, String eventType, String eventId, String fromStatus,
                              String toStatus, String detail, String workflowInstanceId) {
        OaEmployeeHistory history = new OaEmployeeHistory();
        history.setEmployeeId(employee.getId());
        history.setEventId(eventId);
        history.setEventType(eventType);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setDetail(detail);
        history.setWorkflowInstanceId(workflowInstanceId);
        history.setOperatorUserId(currentUserId());
        history.setOperateTime(new Date());
        historyMapper.insert(history);
    }

    /** 工号由服务端生成，唯一键冲突自动重试。 */
    private void insertWithGeneratedNumber(OaEmployee employee) {
        for (int attempt = 0; attempt < GENERATE_RETRY; attempt++) {
            if (StringUtils.isBlank(employee.getEmployeeNo())) {
                employee.setEmployeeNo(numberGenerator.next());
            }
            try {
                baseMapper.insert(employee);
                return;
            } catch (DuplicateKeyException e) {
                employee.setId(null);
                employee.setEmployeeNo(null);
            }
        }
        throw new ServiceException("工号生成冲突，请重试", 409);
    }

    private void validateReferences(Long deptId, Long postId) {
        if (deptId == null) {
            throw new ServiceException("部门不能为空", 400);
        }
        if (postId != null && positionMapper.selectById(postId) == null) {
            throw new ServiceException("岗位不存在", 404);
        }
    }

    private void checkViewAccess(OaEmployee employee) {
        LoginUser user = currentLoginUser();
        boolean allowed = HrAccessPolicy.canView(isSuperAdmin(), isHr(),
            user == null ? null : user.getUserId(), user == null ? null : user.getDeptId(),
            employee.getUserId(), employee.getDeptId(), employee.getDirectLeaderId());
        if (!allowed) {
            throw new ServiceException("没有权限访问员工数据", 403);
        }
    }

    private void checkEditAccess(OaEmployee employee) {
        if (!HrAccessPolicy.canEdit(isSuperAdmin(), isHr())) {
            throw new ServiceException("没有权限修改员工档案", 403);
        }
    }

    private void checkLifecycleAccess() {
        if (!HrAccessPolicy.canManageLifecycle(isSuperAdmin(), isHr())) {
            throw new ServiceException("没有权限办理生命周期命令", 403);
        }
    }

    private void checkImportExportAccess() {
        if (!HrAccessPolicy.canImportExport(isSuperAdmin(), isHr())) {
            throw new ServiceException("没有权限导入导出员工数据", 403);
        }
    }

    private OaEmployeeVo toVo(OaEmployee employee) {
        OaEmployeeVo vo = MapstructUtils.convert(employee, OaEmployeeVo.class);
        if (vo != null && employee.getPostId() != null) {
            OaJobPosition position = positionMapper.selectById(employee.getPostId());
            if (position != null) {
                vo.setPostName(position.getPositionName());
            }
        }
        return vo;
    }

    private List<OaEmployeeVo> toVoList(List<OaEmployee> employees) {
        List<OaEmployeeVo> records = MapstructUtils.convert(employees, OaEmployeeVo.class);
        if (records != null) {
            for (OaEmployeeVo vo : records) {
                if (vo.getPostId() != null) {
                    OaJobPosition position = positionMapper.selectById(vo.getPostId());
                    if (position != null) {
                        vo.setPostName(position.getPositionName());
                    }
                }
            }
        }
        return records;
    }

    private void enrichExport(List<OaEmployee> employees, List<?> rows) {
        Map<Long, String> postNames = new HashMap<>();
        for (OaJobPosition position : positionMapper.selectList(new LambdaQueryWrapper<OaJobPosition>()
            .select(OaJobPosition::getId, OaJobPosition::getPositionName))) {
            postNames.put(position.getId(), position.getPositionName());
        }
        for (int i = 0; i < employees.size(); i++) {
            OaEmployee employee = employees.get(i);
            Object row = rows.get(i);
            String deptName = deptName(employee.getDeptId());
            String postName = employee.getPostId() == null ? null : postNames.get(employee.getPostId());
            if (row instanceof OaEmployeeExportVo vo) {
                vo.setDeptName(deptName);
                vo.setPostName(postName);
            } else if (row instanceof OaEmployeeExportFullVo vo) {
                vo.setDeptName(deptName);
                vo.setPostName(postName);
            }
        }
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDeptVo dept = deptService.selectDeptById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    private LambdaQueryWrapper<OaEmployee> buildQueryWrapper(OaEmployeeBo query, HrPageQuery page) {
        LambdaQueryWrapper<OaEmployee> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(query.getEmployeeNo()), OaEmployee::getEmployeeNo, query.getEmployeeNo())
            .like(StringUtils.isNotBlank(query.getName()), OaEmployee::getName, query.getName())
            .eq(query.getDeptId() != null, OaEmployee::getDeptId, query.getDeptId())
            .eq(query.getPostId() != null, OaEmployee::getPostId, query.getPostId())
            .eq(StringUtils.isNotBlank(query.getStatus()), OaEmployee::getStatus, query.getStatus())
            .eq(query.getUserId() != null, OaEmployee::getUserId, query.getUserId());
        applyOrder(wrapper, page);
        return wrapper;
    }

    /** orderBy 白名单（API 规范 1.6），禁止拼接 SQL。 */
    private void applyOrder(LambdaQueryWrapper<OaEmployee> wrapper, HrPageQuery page) {
        String orderBy = page.getOrderBy();
        boolean asc = !page.desc();
        if (orderBy == null) {
            wrapper.orderBy(true, asc, OaEmployee::getEmployeeNo);
            return;
        }
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, asc, OaEmployee::getName);
            case "entryDate" -> wrapper.orderBy(true, asc, OaEmployee::getEntryDate);
            case "status" -> wrapper.orderBy(true, asc, OaEmployee::getStatus);
            case "createTime" -> wrapper.orderBy(true, asc, OaEmployee::getCreateTime);
            default -> wrapper.orderBy(true, asc, OaEmployee::getEmployeeNo);
        }
    }

    private Map<String, Long> deptIdByName() {
        Map<String, Long> byName = new HashMap<>();
        for (SysDeptVo dept : deptService.selectDeptList(new SysDeptBo())) {
            if (StringUtils.isNotBlank(dept.getDeptName())) {
                byName.put(dept.getDeptName().trim(), dept.getDeptId());
            }
        }
        return byName;
    }

    private Map<String, Long> positionIdByCode() {
        Map<String, Long> byCode = new HashMap<>();
        for (OaJobPosition position : positionMapper.selectList(new LambdaQueryWrapper<OaJobPosition>()
            .select(OaJobPosition::getId, OaJobPosition::getPositionCode))) {
            if (StringUtils.isNotBlank(position.getPositionCode())) {
                byCode.put(position.getPositionCode().trim(), position.getId());
            }
        }
        return byCode;
    }

    private Long currentUserId() {
        return LoginHelper.getUserId();
    }

    private LoginUser currentLoginUser() {
        return LoginHelper.getLoginUser();
    }

    private boolean isSuperAdmin() {
        return LoginHelper.isSuperAdmin();
    }

    private Set<String> currentPermissions() {
        LoginUser user = currentLoginUser();
        return user == null ? null : user.getMenuPermission();
    }

    private boolean isHr() {
        Set<String> permissions = currentPermissions();
        return isSuperAdmin() || (permissions != null && permissions.contains("hr:employee:edit"));
    }
}
