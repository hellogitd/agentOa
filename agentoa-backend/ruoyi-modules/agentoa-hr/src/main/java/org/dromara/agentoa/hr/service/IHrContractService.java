package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaContractBo;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 合同管理服务（需求 HR-09：合同起止、续签提醒）。
 */
public interface IHrContractService {

    PageVo<OaContractVo> selectPageContracts(OaContractBo query, HrPageQuery page);

    List<OaContractVo> selectContracts(OaContractBo query);

    OaContractVo selectContract(Long contractId);

    OaContractVo createContract(OaContractBo bo);

    OaContractVo updateContract(Long contractId, OaContractBo bo);

    void deleteContract(Long contractId);

    /**
     * 续签提醒：days 天内到期（含已过期未处理）的生效合同。
     */
    List<OaContractVo> selectExpiring(int days);
}
