package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.GenericRequestBo;
import org.dromara.agentoa.workflow.domain.bo.PageQuery;
import org.dromara.agentoa.workflow.domain.vo.BusinessRequestVo;
import org.dromara.agentoa.workflow.domain.vo.InstanceStartVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.Map;

/**
 * 通用 OA 申请（M5）：纯 OA 表单绑定轻量业务，直接走通用承接通道的结构化流程链。
 */
public interface IGenericRequestService {

    BusinessRequestVo createDraft(GenericRequestBo bo);

    BusinessRequestVo updateDraft(Long requestId, GenericRequestBo bo);

    BusinessRequestVo selectRequest(Long requestId);

    PageVo<BusinessRequestVo> selectPage(PageQuery page);

    /** @param assigneeSelections 发起人自选审批人（USER_SELECT 节点），可空 */
    InstanceStartVo submit(Long requestId, Integer lockVersion, Map<String, Object> assigneeSelections);

    default InstanceStartVo submit(Long requestId, Integer lockVersion) {
        return submit(requestId, lockVersion, null);
    }

    /** 纯 OA 表单直接发起：建单 + 流程启动一次完成 */
    BusinessRequestVo launch(GenericRequestBo bo);
}
