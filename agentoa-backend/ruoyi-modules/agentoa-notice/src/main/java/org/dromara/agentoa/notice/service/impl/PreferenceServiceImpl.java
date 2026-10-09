package org.dromara.agentoa.notice.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.notice.domain.bo.PreferenceBo;
import org.dromara.agentoa.notice.domain.vo.PreferenceVo;
import org.dromara.agentoa.notice.mapper.NoticePreferenceMapper;
import org.dromara.agentoa.notice.service.IPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知偏好实现：默认全部提醒；关闭仅抑制实时推送，消息仍持久化可补拉。
 */
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements IPreferenceService {

    private static final List<String> DEFAULT_TYPES = List.of("TODO", "NOTICE", "MENTION", "SYSTEM");

    private final NoticePreferenceMapper preferenceMapper;

    @Override
    public List<PreferenceVo> get(Long userId) {
        Map<String, String> stored = new LinkedHashMap<>();
        for (Map<String, Object> row : preferenceMapper.selectByUser(userId)) {
            stored.put(String.valueOf(row.get("msgType")), String.valueOf(row.get("enabled")));
        }
        List<PreferenceVo> result = new ArrayList<>();
        for (String type : DEFAULT_TYPES) {
            PreferenceVo vo = new PreferenceVo();
            vo.setMsgType(type);
            vo.setEnabled(stored.getOrDefault(type, "1"));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PreferenceVo> update(Long userId, List<PreferenceBo> items) {
        Date now = new Date();
        for (PreferenceBo item : items) {
            int enabled = Integer.parseInt(item.getEnabled());
            int rows = preferenceMapper.update(userId, item.getMsgType(), enabled, now);
            if (rows == 0) {
                try {
                    preferenceMapper.insert(userId, item.getMsgType(), enabled, now);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    preferenceMapper.update(userId, item.getMsgType(), enabled, now);
                }
            }
        }
        return get(userId);
    }
}
