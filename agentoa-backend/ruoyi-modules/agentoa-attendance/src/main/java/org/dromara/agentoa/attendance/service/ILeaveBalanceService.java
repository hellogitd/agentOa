package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
import org.dromara.agentoa.attendance.domain.vo.BatchVo;
import org.dromara.agentoa.attendance.domain.vo.LedgerVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 假期额度分钟账本（docs/13 核心规则）：提交冻结、通过转已用、拒绝/撤销释放；
 * 条件更新防透支，账本事件 ID 幂等去重，重复消费不重复扣减。
 * 调休/年假按批次滚动过期（AT-06）：FIFO 先过期先扣，冻结过期保护至结算。
 */
public interface ILeaveBalanceService {

    /** 年度余额（含不计额度假种，availableMinutes 为 null） */
    List<BalanceVo> selectBalances(Long userId, Integer year);

    /** 额度批次（AT-06，按 expire_date 升序） */
    List<BatchVo> selectBatches(Long userId, Integer year, String leaveType);

    /** 发放/回收额度（eventToken 为业务事件 ID，重复调用只生效一次） */
    BalanceVo grant(BalanceGrantBo bo, String eventToken, Long operatorUserId);

    /** 提交冻结 */
    void freeze(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                String leaveType, int minutes, Long operatorUserId);

    /** 通过结算：冻结转已用 */
    void settle(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                String leaveType, int minutes, Long operatorUserId);

    /** 拒绝/撤销释放冻结 */
    void release(String businessType, Long businessId, int submissionNo, Long userId, Integer year,
                 String leaveType, int minutes, Long operatorUserId);

    PageVo<LedgerVo> selectLedger(Long userId, Integer year, String leaveType, AttendancePageQuery page);

    /** 批次过期扫描（幂等，EXPIRE:{batchId} 去重；冻结保护至结算），返回本次过期批次数 */
    int expireBatches(Long operatorUserId);
}
