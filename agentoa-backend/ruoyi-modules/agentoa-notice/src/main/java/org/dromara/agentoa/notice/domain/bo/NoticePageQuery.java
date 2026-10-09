package org.dromara.agentoa.notice.domain.bo;

import lombok.Data;

/**
 * 公告分页查询（API 规范 1.4）。
 */
@Data
public class NoticePageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 状态过滤（空=按可见性返回） */
    private Integer status;

    private String keyword;

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
