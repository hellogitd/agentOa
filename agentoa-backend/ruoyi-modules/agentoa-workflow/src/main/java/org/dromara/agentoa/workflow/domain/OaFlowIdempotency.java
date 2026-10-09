package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_flow_idempotency")
public class OaFlowIdempotency {

    @TableId(value = "id")
    private Long id;

    private String idemKey;

    private Long userId;

    private String requestPath;

    private String bodyDigest;

    private String resultRef;

    private Date createTime;
}
