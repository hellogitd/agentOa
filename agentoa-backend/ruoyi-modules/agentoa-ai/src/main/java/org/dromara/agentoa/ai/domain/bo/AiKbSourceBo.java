package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识域数据源登记（docs/21 AI-M3-02）：document 来源走本 BO，file 来源走 multipart 上传。
 */
@Data
public class AiKbSourceBo {

    @NotBlank(message = "来源类型不能为空")
    private String sourceType;

    /** sourceType=document 时必填 */
    private Long docId;
}
