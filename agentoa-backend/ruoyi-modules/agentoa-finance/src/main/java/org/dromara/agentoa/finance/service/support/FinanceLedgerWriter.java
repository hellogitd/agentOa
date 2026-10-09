package org.dromara.agentoa.finance.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaFinanceEvent;
import org.dromara.agentoa.finance.domain.OaInvoiceAllocation;
import org.dromara.agentoa.finance.domain.OaInvoiceReservation;
import org.dromara.agentoa.finance.mapper.OaFinanceEventMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceAllocationMapper;
import org.dromara.agentoa.finance.mapper.OaInvoiceReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 财务流水写入：唯一事件键是幂等闸门，重复消费只生效一次。
 */
@Component
@RequiredArgsConstructor
public class FinanceLedgerWriter {

    private final OaInvoiceReservationMapper reservationMapper;
    private final OaInvoiceAllocationMapper allocationMapper;
    private final OaFinanceEventMapper eventMapper;

    /** 占用流水（event_key 唯一），返回 false 表示事件已消费 */
    public boolean insertReservation(Long invoiceId, Long reimburseId, String action, String eventKey, Long operatorId) {
        OaInvoiceReservation reservation = new OaInvoiceReservation();
        reservation.setInvoiceId(invoiceId);
        reservation.setReimburseId(reimburseId);
        reservation.setAction(action);
        reservation.setEventKey(eventKey);
        reservation.setOperatorId(operatorId);
        reservation.setCreateTime(new Date());
        try {
            reservationMapper.insert(reservation);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** 发票分摊记录（event_key 唯一），返回 false 表示事件已消费 */
    public boolean insertAllocation(Long invoiceId, Long reimburseId, Long expenseItemId,
                                     BigDecimal amount, String eventKey, Long operatorId) {
        OaInvoiceAllocation allocation = new OaInvoiceAllocation();
        allocation.setInvoiceId(invoiceId);
        allocation.setReimburseId(reimburseId);
        allocation.setExpenseItemId(expenseItemId);
        allocation.setAmount(amount);
        allocation.setEventKey(eventKey);
        allocation.setOperatorId(operatorId);
        allocation.setCreateTime(new Date());
        try {
            allocationMapper.insert(allocation);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** 财务事件（event_key 唯一），返回 false 表示事件已消费 */
    public boolean insertEvent(String eventKey, String bizType, Long bizId, String action,
                               BigDecimal amount, Long operatorId, String remark) {
        OaFinanceEvent event = new OaFinanceEvent();
        event.setEventKey(eventKey);
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setAction(action);
        event.setAmount(amount);
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        event.setCreateTime(new Date());
        try {
            eventMapper.insert(event);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
