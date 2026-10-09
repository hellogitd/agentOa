package org.dromara.agentoa.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.OaEmployeeChange;
import org.dromara.agentoa.hr.domain.OaEmployeeHistory;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaChangeBo;
import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.OaChangeVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.mapper.OaEmployeeChangeMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeHistoryMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.mapper.OaJobPositionMapper;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.agentoa.hr.service.IHrChangeService;
import org.dromara.agentoa.hr.service.support.SalaryCipher;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 员工异动服务实现（API 规范 3.6）。
 * <p>
 * 约束：原部门/岗位/职级/薪资由服务端快照；薪资加密存储（含 nonce/tag 与密钥版本），
 * 明文仅对持 hr:change:sensitive 的调用方返回；生效事件以 CHANGE:{id} 幂等写入员工历史。
 */
@RequiredArgsConstructor
@Service
public class HrChangeServiceImpl implements IHrChangeService {

    private static final Set<String> ORDER_WHITELIST = Set.of("effectiveDate", "createTime", "changeType");
    private static final String PERM_SENSITIVE = "hr:change:sensitive";

    private final OaEmployeeChangeMapper changeMapper;
    private final OaEmployeeMapper employeeMapper;
    private final OaEmployeeHistoryMapper historyMapper;
    private final OaJobPositionMapper positionMapper;

    @Override
    public PageVo<OaChangeVo> selectPageChanges(OaChangeBo query, HrPageQuery page) {
        checkViewAccess();
        Page<OaEmployeeChange> mpPage = new Page<>(page.safePageNum(), page.safePageSize());
        Page<OaEmployeeChange> result = changeMapper.selectPage(mpPage, buildQueryWrapper(query, page));
        return PageVo.of(toVoList(result.getRecords()), result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<OaChangeVo> selectChanges(OaChangeBo query) {
        checkViewAccess();
        return toVoList(changeMapper.selectList(buildQueryWrapper(query, new HrPageQuery())));
    }

    @Override
    public OaChangeVo selectChange(Long changeId) {
        checkViewAccess();
        OaEmployeeChange change = requireChange(changeId);
        return toVo(change);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaChangeVo createChange(OaChangeBo bo) {
        if (!HrAccessPolicy.canManageChange(isSuperAdmin(), currentPermissions())) {
            throw new ServiceException("没有权限发起员工异动", 403);
        }
        if (bo.getChangeType() == null || bo.getChangeType() < OaEmployeeChange.TYPE_TRANSFER
            || bo.getChangeType() > OaEmployeeChange.TYPE_DEMOTION) {
            throw new ServiceException("异动类型取值非法", 400);
        }
        OaEmployee employee = employeeMapper.selectById(bo.getEmployeeId());
        if (employee == null) {
            throw new ServiceException("员工不存在", 404);
        }
        if (bo.getNewDeptId() != null && bo.getNewDeptId().equals(employee.getDeptId())
            && bo.getNewPostId() != null && bo.getNewPostId().equals(employee.getPostId())
            && bo.getNewPositionLevel() != null && bo.getNewPositionLevel().equals(employee.getPositionLevel())
            && bo.getNewSalary() == null) {
            throw new ServiceException("异动目标与当前档案一致", 400);
        }
        validateTarget(bo.getNewDeptId(), bo.getNewPostId());

        OaEmployeeChange change = new OaEmployeeChange();
        change.setEmployeeId(employee.getId());
        change.setChangeType(bo.getChangeType());
        change.setEffectiveDate(bo.getEffectiveDate());
        change.setOldDeptId(employee.getDeptId());
        change.setNewDeptId(bo.getNewDeptId());
        change.setOldPostId(employee.getPostId());
        change.setNewPostId(bo.getNewPostId());
        change.setOldPositionLevel(employee.getPositionLevel());
        change.setNewPositionLevel(bo.getNewPositionLevel());
        change.setOldSalary(employee.getBaseSalary());
        if (bo.getNewSalary() != null) {
            String cipher = SalaryCipher.encrypt(bo.getNewSalary().toPlainString());
            change.setNewSalary(cipher);
            change.setSalaryKeyVersion(SalaryCipher.keyVersionOf(cipher));
        }
        change.setReason(bo.getReason());
        change.setApplied(0);
        change.setFlowInstanceId(bo.getFlowInstanceId());
        change.setSourceRequestId(bo.getSourceRequestId());
        try {
            changeMapper.insert(change);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该来源申请已存在异动记录", 409);
        }
        if (!bo.getEffectiveDate().isAfter(LocalDate.now())) {
            applyChange(change, employee);
        }
        return toVo(change);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyDueChanges() {
        List<OaEmployeeChange> due = changeMapper.selectList(new LambdaQueryWrapper<OaEmployeeChange>()
            .eq(OaEmployeeChange::getApplied, 0)
            .le(OaEmployeeChange::getEffectiveDate, LocalDate.now())
            .orderByAsc(OaEmployeeChange::getEffectiveDate)
            .orderByAsc(OaEmployeeChange::getId));
        int applied = 0;
        for (OaEmployeeChange change : due) {
            OaEmployee employee = employeeMapper.selectById(change.getEmployeeId());
            if (employee == null) {
                continue;
            }
            applyChange(change, employee);
            applied++;
        }
        return applied;
    }

    // ---------------------------------------------------------------- 内部方法

    /** 到期生效：更新档案并写 CHANGE:{id} 幂等历史事件；重复调用只生效一次。 */
    private void applyChange(OaEmployeeChange change, OaEmployee employee) {
        String eventId = "CHANGE:" + change.getId();
        if (historyMapper.selectCount(new LambdaQueryWrapper<OaEmployeeHistory>()
            .eq(OaEmployeeHistory::getEventId, eventId)) > 0) {
            return;
        }
        OaEmployee update = new OaEmployee();
        update.setId(employee.getId());
        if (change.getNewDeptId() != null) {
            update.setDeptId(change.getNewDeptId());
        }
        if (change.getNewPostId() != null) {
            update.setPostId(change.getNewPostId());
        }
        if (StringUtils.isNotBlank(change.getNewPositionLevel())) {
            update.setPositionLevel(change.getNewPositionLevel());
        }
        if (StringUtils.isNotBlank(change.getNewSalary())) {
            update.setBaseSalary(change.getNewSalary());
            update.setSalaryKeyVersion(change.getSalaryKeyVersion());
        }
        employeeMapper.updateById(update);

        OaEmployeeHistory history = new OaEmployeeHistory();
        history.setEmployeeId(employee.getId());
        history.setEventId(eventId);
        history.setEventType("CHANGE");
        history.setFromStatus(employee.getStatus());
        history.setToStatus(employee.getStatus());
        history.setDetail(describe(change));
        history.setWorkflowInstanceId(change.getFlowInstanceId());
        history.setOperatorUserId(LoginHelper.getUserId());
        history.setOperateTime(new Date());
        try {
            historyMapper.insert(history);
        } catch (DuplicateKeyException e) {
            return;
        }
        change.setApplied(1);
        change.setEventId(eventId);
        changeMapper.updateById(change);
    }

    private String describe(OaEmployeeChange change) {
        String type = switch (change.getChangeType() == null ? 0 : change.getChangeType()) {
            case OaEmployeeChange.TYPE_TRANSFER -> "调岗";
            case OaEmployeeChange.TYPE_SALARY -> "调薪";
            case OaEmployeeChange.TYPE_PROMOTION -> "晋升";
            case OaEmployeeChange.TYPE_DEMOTION -> "降级";
            default -> "异动";
        };
        StringBuilder sb = new StringBuilder(type);
        if (change.getNewDeptId() != null) {
            sb.append("，部门 ").append(change.getOldDeptId()).append("->").append(change.getNewDeptId());
        }
        if (change.getNewPostId() != null) {
            sb.append("，岗位 ").append(change.getOldPostId()).append("->").append(change.getNewPostId());
        }
        if (StringUtils.isNotBlank(change.getNewPositionLevel())) {
            sb.append("，职级 ").append(change.getOldPositionLevel()).append("->").append(change.getNewPositionLevel());
        }
        if (StringUtils.isNotBlank(change.getNewSalary())) {
            sb.append("，调薪");
        }
        if (StringUtils.isNotBlank(change.getReason())) {
            sb.append("，").append(change.getReason());
        }
        return sb.toString();
    }

    private void validateTarget(Long deptId, Long postId) {
        if (deptId != null && deptId <= 0) {
            throw new ServiceException("目标部门非法", 400);
        }
        if (postId != null) {
            OaJobPosition position = positionMapper.selectById(postId);
            if (position == null) {
                throw new ServiceException("岗位不存在", 404);
            }
        }
    }

    private OaEmployeeChange requireChange(Long changeId) {
        OaEmployeeChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw new ServiceException("异动记录不存在", 404);
        }
        return change;
    }

    private void checkViewAccess() {
        LoginUser user = LoginHelper.getLoginUser();
        boolean allowed = isSuperAdmin()
            || (currentPermissions() != null && currentPermissions().contains("hr:change:query"))
            || HrAccessPolicy.canView(isSuperAdmin(), false,
            user == null ? null : user.getUserId(), user == null ? null : user.getDeptId(), null, null, null);
        if (!allowed) {
            throw new ServiceException("没有权限查看员工异动", 403);
        }
    }

    private List<OaChangeVo> toVoList(List<OaEmployeeChange> records) {
        List<OaChangeVo> vos = MapstructUtils.convert(records, OaChangeVo.class);
        if (vos != null) {
            for (OaChangeVo vo : vos) {
                enrich(vo);
            }
        }
        return vos;
    }

    private OaChangeVo toVo(OaEmployeeChange change) {
        OaChangeVo vo = MapstructUtils.convert(change, OaChangeVo.class);
        enrich(vo);
        return vo;
    }

    private void enrich(OaChangeVo vo) {
        if (vo == null) {
            return;
        }
        OaEmployee employee = employeeMapper.selectById(vo.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeName(employee.getName());
        }
        boolean sensitive = HrAccessPolicy.canViewChangeSalary(isSuperAdmin(), currentPermissions());
        vo.setOldSalary(salaryForDisplay(vo.getOldSalary(), sensitive));
        vo.setNewSalary(salaryForDisplay(vo.getNewSalary(), sensitive));
    }

    private String salaryForDisplay(String cipher, boolean sensitive) {
        if (StringUtils.isBlank(cipher)) {
            return null;
        }
        if (!sensitive) {
            return SalaryCipher.mask();
        }
        return SalaryCipher.decrypt(cipher);
    }

    private LambdaQueryWrapper<OaEmployeeChange> buildQueryWrapper(OaChangeBo query, HrPageQuery page) {
        LambdaQueryWrapper<OaEmployeeChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getEmployeeId() != null, OaEmployeeChange::getEmployeeId, query.getEmployeeId())
            .eq(query.getChangeType() != null, OaEmployeeChange::getChangeType, query.getChangeType())
            .eq(query.getApplied() != null, OaEmployeeChange::getApplied, query.getApplied());
        if (query.getEffectiveDate() != null) {
            wrapper.le(OaEmployeeChange::getEffectiveDate, query.getEffectiveDate());
        }
        String orderBy = page.getOrderBy();
        boolean asc = !page.desc();
        if (orderBy == null || !ORDER_WHITELIST.contains(orderBy)) {
            wrapper.orderBy(true, false, OaEmployeeChange::getEffectiveDate);
            wrapper.orderBy(true, false, OaEmployeeChange::getId);
            return wrapper;
        }
        switch (orderBy) {
            case "createTime" -> wrapper.orderBy(true, asc, OaEmployeeChange::getCreateTime);
            case "changeType" -> wrapper.orderBy(true, asc, OaEmployeeChange::getChangeType);
            default -> wrapper.orderBy(true, asc, OaEmployeeChange::getEffectiveDate);
        }
        return wrapper;
    }

    private boolean isSuperAdmin() {
        return LoginHelper.isSuperAdmin();
    }

    private Set<String> currentPermissions() {
        LoginUser user = LoginHelper.getLoginUser();
        return user == null ? null : user.getMenuPermission();
    }
}
