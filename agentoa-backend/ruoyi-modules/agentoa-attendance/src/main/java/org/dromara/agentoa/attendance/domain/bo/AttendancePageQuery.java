package org.dromara.agentoa.attendance.domain.bo;

import lombok.Data;

/**
 * 分页查询参数（API 规范 1.4）：orderBy 由服务端白名单限制，禁止拼接 SQL。
 */
@Data
public class AttendancePageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private String orderBy;

    private String orderDirection = "desc";

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
