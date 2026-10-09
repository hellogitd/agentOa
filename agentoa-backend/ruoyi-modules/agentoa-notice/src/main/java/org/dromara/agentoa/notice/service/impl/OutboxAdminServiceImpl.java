package org.dromara.agentoa.notice.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.vo.OutboxEventVo;
import org.dromara.agentoa.notice.mapper.NoticeOutboxMapper;
import org.dromara.agentoa.notice.service.IOutboxAdminService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * outbox 事件管理：失败事件查询与人工重投（重投审计见 sys_oper_log 与重投列）。
 */
@Service
@RequiredArgsConstructor
public class OutboxAdminServiceImpl implements IOutboxAdminService {

    private final NoticeOutboxMapper outboxMapper;

    @Override
    public PageVo<OutboxEventVo> list(String status, NoticePageQuery page) {
        long total = outboxMapper.count(status);
        List<OutboxEventVo> records = outboxMapper.selectPage(status, page.safePageSize(),
            (page.safePageNum() - 1) * page.safePageSize());
        for (OutboxEventVo record : records) {
            record.setTitle(extractTitle(record.getPayload()));
            record.setPayload(null);
        }
        return PageVo.of(records, total, page.safePageNum(), page.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void redeliver(Long id, Long operatorUserId) {
        int rows = outboxMapper.redeliver(id, operatorUserId);
        if (rows == 0) {
            throw new ServiceException("NT_OUTBOX_NOT_REDELIVERABLE 事件不存在或非失败状态", 409);
        }
    }

    private String extractTitle(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(payload);
            return map == null ? null : map.get("title") == null ? null : String.valueOf(map.get("title"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
