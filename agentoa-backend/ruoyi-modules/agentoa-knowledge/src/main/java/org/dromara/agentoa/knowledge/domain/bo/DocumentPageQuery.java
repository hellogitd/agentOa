package org.dromara.agentoa.knowledge.domain.bo;

import lombok.Data;

/**
 * 文档分页查询：列表、回收站与搜索共用（授权过滤在服务端完成）。
 */
@Data
public class DocumentPageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private Long spaceId;

    private Long parentId;

    private String keyword;

    private Integer status;

    /** true 只看回收站，false 只看未删除 */
    private Boolean deleted;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    public boolean deletedOnly() {
        return Boolean.TRUE.equals(deleted);
    }
}
