package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 工作台公告条目（已发布且在受众快照内）。 */
@Data
public class WorkNoticeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String title;

    private String publishTime;
}
