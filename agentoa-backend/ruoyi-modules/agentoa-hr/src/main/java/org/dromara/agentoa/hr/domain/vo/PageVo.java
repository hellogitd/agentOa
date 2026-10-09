package org.dromara.agentoa.hr.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应体（API 规范 1.4）：{records, total, pageNum, pageSize, pages}。
 */
@Data
public class PageVo<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();

    private long total;

    private int pageNum;

    private int pageSize;

    private long pages;

    public static <T> PageVo<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageVo<T> vo = new PageVo<>();
        vo.records = records == null ? new ArrayList<>() : records;
        vo.total = total;
        vo.pageNum = pageNum;
        vo.pageSize = pageSize;
        vo.pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return vo;
    }
}
