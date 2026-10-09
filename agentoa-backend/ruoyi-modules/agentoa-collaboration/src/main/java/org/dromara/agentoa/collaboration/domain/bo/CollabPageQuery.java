package org.dromara.agentoa.collaboration.domain.bo;

import lombok.Data;

/**
 * 分页查询（docs/05 1.4/1.6）：pageNum/pageSize，pageSize 最大 100。
 */
@Data
public class CollabPageQuery {

    private Integer pageNum;

    private Integer pageSize;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
