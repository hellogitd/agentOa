package org.dromara.agentoa.notice.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 公告已读记录（docs/04 7.2）：唯一键 (notice_id, user_id) 保证不重复计数。
 */
@Data
@TableName("oa_announcement_read")
public class OaAnnouncementRead {

    @TableId(value = "id")
    private Long id;

    private Long noticeId;

    private Long userId;

    private Date readTime;
}
