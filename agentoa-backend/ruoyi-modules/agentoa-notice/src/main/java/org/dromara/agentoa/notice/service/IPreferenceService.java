package org.dromara.agentoa.notice.service;

import org.dromara.agentoa.notice.domain.bo.PreferenceBo;
import org.dromara.agentoa.notice.domain.vo.PreferenceVo;

import java.util.List;

/**
 * 通知偏好（docs/15 V8）：仅抑制实时提醒，消息仍持久化可补拉。
 */
public interface IPreferenceService {

    List<PreferenceVo> get(Long userId);

    List<PreferenceVo> update(Long userId, List<PreferenceBo> items);
}
