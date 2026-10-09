package org.dromara.agentoa.ai.domain.bo;

import lombok.Data;

import java.util.Date;

/**
 * 用量查询（docs/21 AI-M1-09）。
 */
@Data
public class AiUsageQueryBo {

    private Long userId;

    private String modelKey;

    private String bizType;

    private Date beginTime;

    private Date endTime;
}
