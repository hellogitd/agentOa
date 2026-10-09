package org.dromara.agentoa.notice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.notice.domain.bo.PreferenceBo;
import org.dromara.agentoa.notice.domain.vo.PreferenceVo;
import org.dromara.agentoa.notice.service.IPreferenceService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知偏好接口（docs/15）：仅抑制实时提醒，消息仍持久化可补拉。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/notice/preferences")
public class NoticePreferenceController {

    private final IPreferenceService preferenceService;

    @GetMapping
    public R<List<PreferenceVo>> get() {
        return R.ok(preferenceService.get(LoginHelper.getUserId()));
    }

    @PutMapping
    public R<List<PreferenceVo>> update(@Valid @RequestBody List<@Valid PreferenceBo> items) {
        return R.ok(preferenceService.update(LoginHelper.getUserId(), items));
    }
}
