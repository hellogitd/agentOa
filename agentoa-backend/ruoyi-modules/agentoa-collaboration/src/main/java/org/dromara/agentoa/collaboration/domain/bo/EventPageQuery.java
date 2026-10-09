package org.dromara.agentoa.collaboration.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 日程列表查询（docs/05 9.1）：start/end 时间范围 + scope */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventPageQuery extends CollabPageQuery {

    /** 范围开始（yyyy-MM-dd 或 RFC 3339） */
    private String start;

    /** 范围结束 */
    private String end;

    /** self=我可见（默认）；organized=我组织的 */
    private String scope;

    private String keyword;
}
