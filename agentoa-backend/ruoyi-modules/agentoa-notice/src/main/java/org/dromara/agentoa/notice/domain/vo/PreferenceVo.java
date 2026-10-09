package org.dromara.agentoa.notice.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知偏好视图。
 */
@Data
public class PreferenceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String msgType;

    /** 是否推送提醒（1是 0否） */
    private String enabled;
}
