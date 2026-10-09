package org.dromara.agentoa.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用量导出对象（docs/21 AI-M1-09）。
 */
@Data
@ExcelIgnoreUnannotated
public class AiUsageExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "时间")
    private Date createTime;

    @ExcelProperty(value = "账号")
    private String username;

    @ExcelProperty(value = "模型")
    private String modelKey;

    @ExcelProperty(value = "业务类型")
    private String bizType;

    @ExcelProperty(value = "输入 token")
    private Integer promptTokens;

    @ExcelProperty(value = "输出 token")
    private Integer completionTokens;

    @ExcelProperty(value = "合计 token")
    private Integer totalTokens;

    @ExcelProperty(value = "耗时(ms)")
    private Integer latencyMs;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "错误码")
    private String errorCode;
}
