package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 写接口 Idempotency-Key 记录。唯一范围：当前用户 + Key，保留 24 小时（API 规范 1.7）。
 */
@Data
@TableName("oa_idempotency")
public class OaIdempotency {

    @TableId(value = "id")
    private Long id;

    private String idemKey;

    private Long userId;

    private String requestPath;

    /** 请求体摘要（SHA-256） */
    private String bodyDigest;

    /** 首次结果引用（如员工 ID），重放时直接返回 */
    private String resultRef;

    private Date createTime;
}
