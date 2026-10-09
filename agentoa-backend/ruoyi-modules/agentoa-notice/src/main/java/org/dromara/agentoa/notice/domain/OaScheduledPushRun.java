package org.dromara.agentoa.notice.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时推送执行记录 oa_scheduled_push_run（NC-04）：event_key 幂等去重 + 执行历史。
 */
@Data
@TableName("oa_scheduled_push_run")
public class OaScheduledPushRun {

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(value = "id")
    private Long id;

    private Long pushId;

    /** SCHED-{pushId}-{slot} 幂等键 */
    private String eventKey;

    private LocalDateTime slotTime;

    /** 结果（1成功 2失败） */
    private Integer status;

    private Integer receiverCount;

    private String error;

    private LocalDateTime createTime;
}
