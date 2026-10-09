package org.dromara.agentoa.ai.domain.bo;

import lombok.Data;

/**
 * 分页查询参数（API 规范 1.4）：pageNum/pageSize/orderBy/orderDirection。
 * orderBy 白名单校验，禁止拼接 SQL。
 */
@Data
public class AiPageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 排序字段（白名单） */
    private String orderBy;

    /** asc / desc */
    private String orderDirection = "asc";

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    public boolean desc() {
        return "desc".equalsIgnoreCase(orderDirection);
    }
}
