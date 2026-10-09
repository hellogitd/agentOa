package org.dromara.agentoa.notice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时推送执行记录视图（NC-04）。
 */
@Data
public class PushRunVo {

    private Long id;

    private Long pushId;

    private String eventKey;

    private LocalDateTime slotTime;

    /** 结果（1成功 2失败） */
    private Integer status;

    private Integer receiverCount;

    private String error;

    private LocalDateTime createTime;
}
