package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.CorrectionRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.Map;

public interface ICorrectionRequestService {

    BusinessRequestVo createDraft(CorrectionRequestBo bo);

    BusinessRequestVo updateDraft(Long requestId, CorrectionRequestBo bo);

    BusinessRequestVo selectRequest(Long requestId);

    PageVo<BusinessRequestVo> selectPage(PageQuery page);

    /** @param assigneeSelections 发起人自选审批人（USER_SELECT 节点），可空 */
    InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections);

    default InstanceStartVo submit(Long requestId, Integer lockVersion) {
        return submit(requestId, lockVersion, null);
    }
}