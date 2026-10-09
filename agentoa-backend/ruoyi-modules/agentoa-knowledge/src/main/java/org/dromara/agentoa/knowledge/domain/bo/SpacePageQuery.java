package org.dromara.agentoa.knowledge.domain.bo;

import lombok.Data;

/**
 * 知识空间分页查询（API 规范 1.4）。
 */
@Data
public class SpacePageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private String keyword;

    /** 空间类型过滤（1公开 2私密 3团队） */
    private Integer spaceType;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
