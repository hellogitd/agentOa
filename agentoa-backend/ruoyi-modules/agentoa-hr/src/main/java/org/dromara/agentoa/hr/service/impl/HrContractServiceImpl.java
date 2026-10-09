package org.dromara.agentoa.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaContract;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaContractBo;
import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.mapper.OaContractMapper;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.dromara.agentoa.hr.service.IHrContractService;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 合同管理服务实现（需求 HR-09）。
 * <p>
 * 约束：状态由服务端维护（生效/到期/终止），到期按 end_date 计算；
 * 读取与导出复用员工档案的对象级授权。
 */
@RequiredArgsConstructor
@Service
public class HrContractServiceImpl implements IHrContractService {

    private final OaContractMapper contractMapper;
    private final OaEmployeeMapper employeeMapper;

    @Override
    public PageVo<OaContractVo> selectPageContracts(OaContractBo query, HrPageQuery page) {
        checkViewAccess();
        Page<OaContract> mpPage = new Page<>(page.safePageNum(), page.safePageSize());
        Page<OaContract> result = contractMapper.selectPage(mpPage, buildQueryWrapper(query, page));
        return PageVo.of(toVoList(result.getRecords()), result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<OaContractVo> selectContracts(OaContractBo query) {
        checkViewAccess();
        return toVoList(contractMapper.selectList(buildQueryWrapper(query, new HrPageQuery())));
    }

    @Override
    public OaContractVo selectContract(Long contractId) {
        checkViewAccess();
        return toVo(requireContract(contractId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaContractVo createContract(OaContractBo bo) {
        checkManageAccess();
        requireEmployee(bo.getEmployeeId());
        validateDates(bo);
        if (StringUtils.isNotBlank(bo.getContractNo())
            && contractMapper.exists(new LambdaQueryWrapper<OaContract>().eq(OaContract::getContractNo, bo.getContractNo()))) {
            throw new ServiceException("合同编号'" + bo.getContractNo() + "'已存在", 409);
        }
        OaContract contract = MapstructUtils.convert(bo, OaContract.class);
        contract.setId(null);
        if (contract.getRenewCount() == null) {
            contract.setRenewCount(0);
        }
        contract.setStatus(resolveStatus(contract));
        contractMapper.insert(contract);
        return toVo(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaContractVo updateContract(Long contractId, OaContractBo bo) {
        checkManageAccess();
        OaContract existing = requireContract(contractId);
        validateDates(bo);
        if (StringUtils.isNotBlank(bo.getContractNo()) && !bo.getContractNo().equals(existing.getContractNo())
            && contractMapper.exists(new LambdaQueryWrapper<OaContract>().eq(OaContract::getContractNo, bo.getContractNo()))) {
            throw new ServiceException("合同编号'" + bo.getContractNo() + "'已存在", 409);
        }
        OaContract contract = MapstructUtils.convert(bo, OaContract.class);
        contract.setId(contractId);
        contract.setEmployeeId(existing.getEmployeeId());
        if (contract.getRenewCount() == null) {
            contract.setRenewCount(existing.getRenewCount());
        }
        if (bo.getStatus() == null) {
            contract.setStatus(resolveStatus(contract));
        }
        contractMapper.updateById(contract);
        return toVo(requireContract(contractId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContract(Long contractId) {
        checkManageAccess();
        requireContract(contractId);
        contractMapper.deleteById(contractId);
    }

    @Override
    public List<OaContractVo> selectExpiring(int days) {
        checkViewAccess();
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(Math.max(0, days));
        List<OaContract> expiring = contractMapper.selectList(new LambdaQueryWrapper<OaContract>()
            .eq(OaContract::getStatus, OaContract.STATUS_ACTIVE)
            .isNotNull(OaContract::getEndDate)
            .le(OaContract::getEndDate, until)
            .orderByAsc(OaContract::getEndDate));
        return toVoList(expiring);
    }

    // ---------------------------------------------------------------- 内部方法

    /** 生效状态按 end_date 服务端计算，客户端提供的状态仅用于终止等显式覆盖。 */
    private int resolveStatus(OaContract contract) {
        if (contract.getStatus() != null && contract.getStatus() == OaContract.STATUS_TERMINATED) {
            return OaContract.STATUS_TERMINATED;
        }
        if (contract.getEndDate() != null && contract.getEndDate().isBefore(LocalDate.now())) {
            return OaContract.STATUS_EXPIRED;
        }
        return OaContract.STATUS_ACTIVE;
    }

    private void validateDates(OaContractBo bo) {
        if (bo.getEndDate() != null && bo.getEndDate().isBefore(bo.getStartDate())) {
            throw new ServiceException("合同结束日期不能早于开始日期", 400);
        }
        if (bo.getStatus() != null && (bo.getStatus() < OaContract.STATUS_ACTIVE
            || bo.getStatus() > OaContract.STATUS_TERMINATED)) {
            throw new ServiceException("合同状态取值非法", 400);
        }
    }

    private OaContract requireContract(Long contractId) {
        OaContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("合同不存在", 404);
        }
        return contract;
    }

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

    private void checkViewAccess() {
        LoginUser user = LoginHelper.getLoginUser();
        boolean allowed = isSuperAdmin()
            || (currentPermissions() != null && (currentPermissions().contains("hr:contract:query")
            || currentPermissions().contains("hr:contract:list")))
            || HrAccessPolicy.canView(isSuperAdmin(), false,
            user == null ? null : user.getUserId(), user == null ? null : user.getDeptId(), null, null, null);
        if (!allowed) {
            throw new ServiceException("没有权限查看合同", 403);
        }
    }

    private void checkManageAccess() {
        if (!HrAccessPolicy.canManageHrRecords(isSuperAdmin(), currentPermissions())) {
            throw new ServiceException("没有权限维护合同", 403);
        }
    }

    private List<OaContractVo> toVoList(List<OaContract> records) {
        List<OaContractVo> vos = MapstructUtils.convert(records, OaContractVo.class);
        if (vos != null) {
            for (OaContractVo vo : vos) {
                enrich(vo);
            }
        }
        return vos;
    }

    private OaContractVo toVo(OaContract contract) {
        OaContractVo vo = MapstructUtils.convert(contract, OaContractVo.class);
        enrich(vo);
        return vo;
    }

    private void enrich(OaContractVo vo) {
        if (vo == null) {
            return;
        }
        if (vo.getEmployeeId() != null) {
            OaEmployee employee = employeeMapper.selectById(vo.getEmployeeId());
            if (employee != null) {
                vo.setEmployeeName(employee.getName());
                vo.setEmployeeNo(employee.getEmployeeNo());
            }
        }
        if (vo.getEndDate() != null) {
            vo.setDaysToExpire(ChronoUnit.DAYS.between(LocalDate.now(), vo.getEndDate()));
        }
    }

    private LambdaQueryWrapper<OaContract> buildQueryWrapper(OaContractBo query, HrPageQuery page) {
        LambdaQueryWrapper<OaContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getEmployeeId() != null, OaContract::getEmployeeId, query.getEmployeeId())
            .like(StringUtils.isNotBlank(query.getContractNo()), OaContract::getContractNo, query.getContractNo())
            .eq(StringUtils.isNotBlank(query.getContractType()), OaContract::getContractType, query.getContractType())
            .eq(query.getStatus() != null, OaContract::getStatus, query.getStatus());
        String orderBy = page.getOrderBy();
        boolean asc = !page.desc();
        if ("startDate".equals(orderBy)) {
            wrapper.orderBy(true, asc, OaContract::getStartDate);
        } else if ("endDate".equals(orderBy)) {
            wrapper.orderBy(true, asc, OaContract::getEndDate);
        } else {
            wrapper.orderBy(true, false, OaContract::getCreateTime);
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
