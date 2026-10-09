package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaChangeBo;
import org.dromara.agentoa.hr.domain.vo.OaChangeVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 员工异动服务（API 规范 3.6 调岗调薪）。
 * <p>
 * 异动按 effective_date 生效：当日及以前立即生效，未来日期由 {@link #applyDueChanges()} 到期执行；
 * source_request_id 与生效事件双重去重，重复提交/并发只产生一个生效事件。
 */
public interface IHrChangeService {

    PageVo<OaChangeVo> selectPageChanges(OaChangeBo query, HrPageQuery page);

    List<OaChangeVo> selectChanges(OaChangeBo query);

    OaChangeVo selectChange(Long changeId);

    /** 发起异动（调岗/调薪/晋升/降级） */
    OaChangeVo createChange(OaChangeBo bo);

    /** 执行所有到期未生效的异动，返回本次生效条数（可重复调用，幂等） */
    int applyDueChanges();
}
